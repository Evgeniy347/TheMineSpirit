import json
import os
import re
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import journal as journal_module
from journal import EVENTS, Journal
from state import default_state, StateStore
from tests.helpers import FakeClock

APP = Path(__file__).resolve().parent.parent
LOG_CALL = re.compile(r'\.log\(\s*"([A-Za-z_]+)"')


class JournalTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)
        self.clock = FakeClock()
        self.journal = Journal(self.dir / "journal.jsonl", self.clock, max_bytes=10_000, files=3)

    def test_log_RoundTrip_WritesSchemaTimestampAndFields(self):
        self.journal.log("decision", cycle=3, bot="spirit_1", model="m", prompt_sha="abc", reason="лес рядом",
                         plan={"type": "script"}, outcome="ok")
        result = self.journal.read()
        self.assertEqual([], result.bad)
        self.assertEqual(1, len(result.events))
        event = result.events[0]
        self.assertEqual(1, event["schema"])
        self.assertEqual("decision", event["event"])
        self.assertRegex(event["ts"], r"^2027-01-15T\d\d:\d\d:\d\d", "ts — ISO-время UTC из часов")
        self.assertEqual(("spirit_1", 3, "лес рядом", {"type": "script"}), (event["bot"], event["cycle"], event["reason"], event["plan"]))

    def test_log_NoneFields_Omitted(self):
        self.journal.log("cycle_start", cycle=1, bot=None, reason=None)
        event = self.journal.read().events[0]
        self.assertNotIn("bot", event)
        self.assertNotIn("reason", event)

    def test_log_UnknownEvent_RaisesValueError(self):
        with self.assertRaises(ValueError) as ctx:
            self.journal.log("придуманное", cycle=1)
        self.assertIn("придуманное", str(ctx.exception))

    def test_log_LongReply_TruncatedTo4KiBOnCharBoundary(self):
        reply = "a" + "ёж" * 5000
        self.journal.log("decision", cycle=1, bot="b", reply=reply)
        event = self.journal.read().events[0]
        self.assertLessEqual(len(event["reply"].encode("utf-8")), 4096 + 40, "ответ модели в журнале — до 4 КиБ (плюс пометка)")
        self.assertTrue(event["reply_truncated"], "усечение помечено")
        self.assertIn("усечено", event["reply"])
        self.assertTrue(reply.startswith(event["reply"].split("…[")[0]), "сохранено начало ответа")

    def test_log_ShortReply_NotTouched(self):
        self.journal.log("decision", cycle=1, bot="b", reply="короткий")
        event = self.journal.read().events[0]
        self.assertEqual("короткий", event["reply"])
        self.assertNotIn("reply_truncated", event)

    def test_rotation_KeepsFileCountAndDropsOldest(self):
        journal = Journal(self.dir / "r.jsonl", self.clock, max_bytes=1000, files=3)
        for i in range(120):
            journal.log("bot_skipped", cycle=i, bot="b", reason="x" * 40)
        names = sorted(p.name for p in self.dir.glob("r.jsonl*"))
        self.assertEqual(["r.jsonl", "r.jsonl.1", "r.jsonl.2"], names, "текущий файл и два архива, третьего нет")
        for name in names:
            self.assertLessEqual((self.dir / name).stat().st_size, 1000, f"{name} превысил предел ротации")
        cycles = [e["cycle"] for e in journal.read().events]
        self.assertEqual(119, cycles[-1], "последнее событие на месте")
        self.assertEqual(sorted(cycles), cycles, "события читаются от старых к новым")
        self.assertLess(len(cycles), 120, "самые старые события выброшены ротацией")
        self.assertEqual(list(range(cycles[0], 120)), cycles, "в журнале нет дыр посередине")

    def test_rotation_SingleFile_DropsInsteadOfArchiving(self):
        journal = Journal(self.dir / "s.jsonl", self.clock, max_bytes=500, files=1)
        for i in range(50):
            journal.log("bot_skipped", cycle=i, bot="b", reason="x" * 40)
        self.assertEqual(["s.jsonl"], sorted(p.name for p in self.dir.glob("s.jsonl*")))

    def test_read_UnknownSchemaAndGarbage_ReportedNotSilentlyDropped(self):
        path = self.dir / "journal.jsonl"
        good = json.dumps({"schema": 1, "ts": "t", "event": "alert", "reason": "ok"}, ensure_ascii=False)
        future = json.dumps({"schema": 2, "ts": "t", "event": "alert"})
        path.write_text("\n".join([good, future, "не json", good]) + "\n", encoding="utf-8")
        result = self.journal.read()
        self.assertEqual(2, len(result.events))
        self.assertEqual(2, len(result.bad))
        reasons = " ".join(reason for _, _, reason in result.bad)
        self.assertIn("schema", reasons)
        self.assertIn("JSON", reasons)

    def test_read_MissingFile_Empty(self):
        self.assertEqual(([], []), (self.journal.read().events, self.journal.read().bad))

    def test_recentDecisions_DryWouldPostIncludedRealPostNot(self):
        self.journal.log("posted", cycle=1, bot="b1", dry=True, outcome="would_post", goal="добыть уголь")
        self.journal.log("posted", cycle=2, bot="b1", dry=False, goal="настоящая задача", request_id="r", task_id="t")
        recent = self.journal.recent_decisions("b1", 5)
        self.assertEqual(["would_post"], [e.get("outcome") for e in recent],
                         "в недавние решения попадает только dry «отправил бы»: модель видит, что уже решила; "
                         "настоящая задача видна по task_done, а не по posted")
        self.assertEqual("добыть уголь", recent[0]["goal"])

    def test_recentDecisions_LastFiveOfBotWithOutcomeAndAge(self):
        for i in range(8):
            self.clock.advance(60)
            self.journal.log("task_done", cycle=i, bot="b1", goal=f"цель {i}", outcome="failed", error="нет руды")
            self.journal.log("task_done", cycle=i, bot="b2", goal="чужая", outcome="succeeded")
        self.journal.log("rejected", cycle=9, bot="b1", goal="плохой план", reason="скрипта нет в каталоге")
        self.journal.log("decision", cycle=10, bot="b1", skip=True, reason="ждать")
        self.journal.log("observed", cycle=10, bot="b1")
        recent = self.journal.recent_decisions("b1", 5)
        self.assertEqual(5, len(recent))
        self.assertEqual(["task_done", "task_done", "task_done", "rejected", "decision"], [e["event"] for e in recent],
                         "последние 5 решений духа в хронологическом порядке; observed не считается решением")
        self.assertNotIn("чужая", [e.get("goal") for e in recent], "решения других духов не попадают")
        self.assertTrue(all(e["bot"] == "b1" for e in recent))

    def test_eventNames_InSourceAreAllInCatalogue(self):
        sources = [p for p in APP.glob("*.py")]
        self.assertTrue(sources, "сторож должен видеть исходники агента")
        used = {}
        for path in sources:
            for name in LOG_CALL.findall(path.read_text(encoding="utf-8")):
                used.setdefault(name, path.name)
        unknown = {name: file for name, file in used.items() if name not in EVENTS}
        self.assertEqual({}, unknown, "имена событий журнала должны входить в journal.EVENTS: добавьте в перечень "
                                     "или исправьте опечатку")

    def test_eventNames_EveryCatalogueNameIsUsedInSource(self):
        used = set()
        for path in APP.glob("*.py"):
            used.update(LOG_CALL.findall(path.read_text(encoding="utf-8")))
        self.assertEqual([], sorted(set(EVENTS) - used), "имя из journal.EVENTS нигде не пишется в журнал: уберите его из "
                                                          "перечня или допишите событие")

    def test_eventNamesGuard_PatternFindsViolationInSample(self):
        sample = 'self.journal.log("bogus_event", cycle=1)\njournal.log(\n "multi_line")'
        self.assertEqual(["bogus_event", "multi_line"], LOG_CALL.findall(sample),
                         "сторож не слеп: находит вызов с неизвестным именем, в том числе перенесённый на другую строку")

    def test_events_Catalogue_MatchesChecklist(self):
        self.assertEqual({"cycle_start", "bot_skipped", "observed", "decision", "rejected", "posted", "task_state",
                          "task_done", "brain_failure", "alert"}, set(EVENTS))
        self.assertEqual(1, journal_module.SCHEMA)


class StateTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)
        self.clock = FakeClock()
        self.store = StateStore(self.dir / "state.json", self.clock)
        self.journal = Journal(self.dir / "journal.jsonl", self.clock, max_bytes=10_000, files=2)

    def test_load_MissingFile_GivesFreshDefaults(self):
        loaded = self.store.load(self.journal)
        self.assertEqual((0, {}, {}), (loaded.state["cycle"], loaded.state["active"], loaded.state["pending"]))
        self.assertIsNone(loaded.corrupt_path)
        self.assertEqual([], self.journal.read().events, "отсутствие файла — не авария")

    def test_saveLoad_RoundTrip(self):
        state = self.store.load(self.journal).state
        state["cycle"] = 7
        state["active"]["t-1"] = {"bot": "b", "goal": "г", "request_id": "r", "fingerprint": "f", "posted_at": 1.5}
        self.store.save(state)
        again = self.store.load(self.journal).state
        self.assertEqual(7, again["cycle"])
        self.assertEqual("b", again["active"]["t-1"]["bot"])
        self.assertEqual(["state.json"], [p.name for p in self.dir.glob("state*")], "временных файлов не остаётся")

    def test_save_SameStateTwice_DoesNotTouchDisk(self):
        state = self.store.load(self.journal).state
        state["cycle"] = 1
        with mock.patch("os.replace", wraps=os.replace) as replace:
            self.store.save(state)
            self.store.save(state)
            self.assertEqual(1, replace.call_count,
                            "повторное сохранение неизменного состояния не должно перезаписывать файл: каждая запись — "
                            "fsync, а save() зовут после каждого цикла и каждого опроса задачи")
            state["cycle"] = 2
            self.store.save(state)
            self.assertEqual(2, replace.call_count, "состояние изменилось — записать обязательно")
        self.assertEqual(2, self.store.load(self.journal).state["cycle"], "на диске последнее состояние")

    def test_save_AfterCorruptLoad_WritesAgain(self):
        self.store.save(default_state())
        (self.dir / "state.json").write_text('{"schema": 1, "cycle": ', encoding="utf-8")
        self.store.load(self.journal)
        with mock.patch("os.replace", wraps=os.replace) as replace:
            self.store.save(default_state())
            self.assertEqual(1, replace.call_count, "после карантина битого файла кэш записи сброшен: состояние "
                                                   "обязано лечь на диск заново")

    def test_save_FailureBeforeRename_KeepsPreviousStateIntact(self):
        state = self.store.load(self.journal).state
        state["cycle"] = 1
        self.store.save(state)
        state["cycle"] = 2
        with mock.patch("os.replace", side_effect=OSError("диск отвалился")):
            with self.assertRaises(OSError):
                self.store.save(state)
        self.assertEqual(1, self.store.load(self.journal).state["cycle"], "запись атомарна: старое состояние цело")
        self.assertEqual(["state.json"], [p.name for p in self.dir.glob("state*")], "после сбоя временный файл убран")

    def test_load_CorruptFile_RenamedAndAlertLogged(self):
        path = self.dir / "state.json"
        path.write_text('{"schema": 1, "cycle": ', encoding="utf-8")
        loaded = self.store.load(self.journal)
        self.assertEqual(0, loaded.state["cycle"])
        self.assertIsNotNone(loaded.corrupt_path, "повреждённое состояние — не тихий сброс")
        self.assertTrue(loaded.corrupt_path.name.startswith("state.json.corrupt-"))
        self.assertEqual('{"schema": 1, "cycle": ', loaded.corrupt_path.read_text(encoding="utf-8"), "содержимое сохранено")
        self.assertFalse(path.exists(), "битый файл убран с дороги")
        alerts = [e for e in self.journal.read().events if e["event"] == "alert"]
        self.assertEqual(1, len(alerts))
        self.assertIn("state.json.corrupt-", alerts[0]["reason"])

    def test_load_UnknownSchemaOrWrongShape_TreatedAsCorrupt(self):
        cases = {"чужая schema": {"schema": 99, "cycle": 1}, "не объект": [1, 2], "active не объект": {"schema": 1, "active": []},
                 "cycle не число": {"schema": 1, "cycle": "много"}}
        for name, content in cases.items():
            with self.subTest(case=name):
                (self.dir / "state.json").write_text(json.dumps(content), encoding="utf-8")
                loaded = self.store.load(self.journal)
                self.assertIsNotNone(loaded.corrupt_path, f"«{name}» должно считаться повреждением")
                self.assertEqual(0, loaded.state["cycle"])
                self.clock.advance(1)


if __name__ == "__main__":
    unittest.main()
