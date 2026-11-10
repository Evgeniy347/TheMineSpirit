import gzip
import io
import json
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

HERE = Path(__file__).resolve().parent
LOGDB = HERE.parent
sys.path.insert(0, str(LOGDB))
sys.path.insert(0, str(LOGDB.parent))
sys.path.insert(0, str(LOGDB.parent / "lead"))
import loader
import logq
import mod_version
import live

FIXTURE = LOGDB / "fixtures" / "legacy.log"


def record(kind, **fields):
    data = {
        "type": kind,
        "ts": 1_700_000_000_000,
        "tick": 1,
        "build": "1.0+test",
        "source": "live",
    }
    data.update(fields)
    return data


def block(index=1):
    return record(
        "block_change",
        actor_type="spirit",
        actor="дух-1",
        action="break",
        block_before="Block{minecraft:stone}",
        block_after="Block{minecraft:air}",
        x=index,
        y=64,
        z=index,
        cause="break",
    )


class LoaderTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.dir = Path(self.temp.name)
        self.events = self.dir / "events"
        self.events.mkdir()
        self.db = self.dir / "events.db"
        self.conn = loader.connect(self.db)
        loader.ensure_schema(self.conn)

    def tearDown(self):
        self.conn.close()
        self.temp.cleanup()

    def write(self, name, rows, newline=True):
        text = "\n".join(json.dumps(row, ensure_ascii=False) for row in rows)
        if newline:
            text += "\n"
        (self.events / name).write_text(text, encoding="utf-8")

    def count(self, table):
        return self.conn.execute('SELECT COUNT(*) FROM "%s"' % table).fetchone()[0]

    def test_jsonlSecondRun_DoesNotDuplicate(self):
        self.write("events-00001.jsonl", [block(1), block(2)])
        self.assertEqual(2, loader.ingest_events_dir(self.conn, self.events))
        self.assertEqual(0, loader.ingest_events_dir(self.conn, self.events))
        self.assertEqual(2, self.count("block_changes"))

    def test_incompleteLastLine_IsReadAfterNewline(self):
        line = json.dumps(block(1), ensure_ascii=False)
        (self.events / "events-00001.jsonl").write_text(line, encoding="utf-8")
        self.assertEqual(0, loader.ingest_events_dir(self.conn, self.events))
        with (self.events / "events-00001.jsonl").open("a", encoding="utf-8") as stream:
            stream.write("\n" + json.dumps(block(2), ensure_ascii=False) + "\n")
        self.assertEqual(2, loader.ingest_events_dir(self.conn, self.events))
        self.assertEqual(2, self.count("block_changes"))

    def test_rotation_ReadsEveryFile(self):
        self.write("events-00001.jsonl", [block(1)])
        self.write("events-00002.jsonl", [block(2)])
        self.assertEqual(2, loader.ingest_events_dir(self.conn, self.events))
        self.assertEqual(2, self.count("block_changes"))

    def test_unknownType_GoesToMessagesNotCrash(self):
        self.write("events-00001.jsonl", [{"type": "no_such", "ts": 1, "tick": 0}])
        loader.ingest_events_dir(self.conn, self.events)
        self.assertEqual(0, self.count("block_changes"))
        self.assertEqual(1, self.count("messages"))

    def test_truncatedFile_DropsItsRowsAndReadsAgain(self):
        self.write("events-00001.jsonl", [block(1), block(2)])
        loader.ingest_events_dir(self.conn, self.events)
        (self.events / "events-00001.jsonl").write_text("", encoding="utf-8")
        loader.ingest_events_dir(self.conn, self.events)
        self.assertEqual(0, self.count("block_changes"))
        self.write("events-00001.jsonl", [block(3)])
        loader.ingest_events_dir(self.conn, self.events)
        self.assertEqual(1, self.count("block_changes"))

    def test_schema_HasVersionAndMigrationOnce(self):
        version = self.conn.execute("SELECT value FROM meta WHERE key='schema_version'").fetchone()[0]
        loader.ensure_schema(self.conn)
        migrations = self.conn.execute("SELECT COUNT(*) FROM migrations").fetchone()[0]
        self.assertEqual(str(loader.VERSION), version)
        self.assertEqual(1, migrations)

    def test_text_AllFourCoordinateFormats_AreParsed(self):
        counters = loader.ingest_text(self.conn, FIXTURE)
        self.conn.commit()
        self.assertEqual(4, counters["typed"].get("block_changes"))
        points = self.conn.execute(
            "SELECT x, y, z FROM block_changes ORDER BY ts"
        ).fetchall()
        self.assertEqual([(-181, 68, -518), (-182, 69, -519), (-183, 69, -520), (-184, 70, -521)], points)

    def test_text_MojibakeIncident_IsRepaired(self):
        counters = loader.ingest_text(self.conn, FIXTURE)
        self.conn.commit()
        self.assertGreaterEqual(counters["typed"].get("task_events", 0), 2)
        reason = self.conn.execute(
            "SELECT reason FROM task_events WHERE event='FAILED'"
        ).fetchone()[0]
        self.assertIn("нет пути", reason)

    def test_text_AllTypedTables_AreFilled(self):
        counters = loader.ingest_text(self.conn, FIXTURE)
        self.conn.commit()
        for table in ("block_changes", "task_events", "deaths", "routes", "stuck", "item_moves", "spirit_samples", "server_health", "graph_decisions"):
            self.assertGreater(self.count(table), 0, table)
        self.assertGreater(counters["messages"], 0)

    def test_logq_cell_AcceptsEveryCoordinateFormat(self):
        loader.ingest_text(self.conn, FIXTURE)
        self.conn.commit()
        self.conn.close()
        for cell in (
            ["-181,68,-518"],
            ["(-182, 69, -519)"],
            ["BlockCoord[x=-183, y=69, z=-520]"],
            ["(x=-184.5, y=70.0, z=-521.5)"],
        ):
            output = subprocess.run(
                [sys.executable, str(LOGDB / "logq.py"), "--db", str(self.db), "--json", "--limit", "5", "cell", *cell],
                capture_output=True,
                check=True,
                text=True,
                encoding="utf-8",
            ).stdout
            rows = json.loads(output)
            self.assertTrue(rows, cell)
        self.conn = loader.connect(self.db)

    def test_logq_sql_RejectsWrites(self):
        result = subprocess.run(
            [sys.executable, str(LOGDB / "logq.py"), "--db", str(self.db), "sql", "DELETE FROM block_changes"],
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("только чтение", result.stderr + result.stdout)

    def write_log(self, name, lines):
        path = self.dir / name
        path.write_text("".join(line + "\n" for line in lines), encoding="utf-8")
        return path

    def test_remoteCommand_ReadsOnlyWithoutJsonlIngest(self):
        arguments = ["--main", "cell", "--", "-177", "68", "-517"]
        command = logq.remote_command(arguments, "/db/events.db")
        self.assertNotIn("--logs", command, "основная база пишется только cron, logq --main не дочитывает JSONL")
        self.assertEqual(["cell", "--", "-177", "68", "-517"], command[-5:])
        for arguments, tail in (
            (["--main", "failures", "--since", "1h"], ["failures", "--since", "1h"]),
            (["--main", "deaths", "--since", "30m"], ["deaths", "--since", "30m"]),
            (["--main", "stuck", "--since", "2h"], ["stuck", "--since", "2h"]),
            (["--main", "health", "--since", "10m"], ["health", "--since", "10m"]),
            (["--main", "grep", "застрял", "--since", "1h"], ["grep", "застрял", "--since", "1h"]),
            (["--main", "text", "пути", "нет", "--since", "1h"], ["text", "пути", "нет", "--since", "1h"]),
            (["--main", "items", "oak_log", "--since", "1h"], ["items", "oak_log", "--since", "1h"]),
            (["--main", "blocks", "--spirit", "spirit-1", "--since", "1h"], ["blocks", "--spirit", "spirit-1", "--since", "1h"]),
        ):
            command = logq.remote_command(arguments, "/db/events.db")
            self.assertEqual(
                tail,
                command[-len(tail):],
                "logq --main теряет аргументы команды %s: сервер вернул бы старые строки без --since "
                "(мониторинг считал по ts в SQL)" % (arguments,),
            )
        self.assertEqual(
            ["--db", "/db/events.db", "--json", "--limit", "30", "deaths", "--since", "30m"],
            logq.remote_command(["--main", "deaths", "--since", "30m"], "/db/events.db")[-8:],
            "глобальные опции локальной цели не уезжают на сервер: --main/--json/--db/--logs/--limit задаёт "
            + "получатель, а команда и её --since — отправитель",
        )
        self.assertEqual(
            ["--limit", "5", "stuck"],
            logq.remote_command(["--main", "--limit", "5", "stuck"], "/db/events.db")[-3:],
            "свой --limit передаётся серверу один раз и его значением",
        )
        self.assertEqual(
            ["stuck"],
            logq.remote_command(["--main", "--limit=7", "stuck"], "/db/events.db")[-1:],
            "форма --limit=7 не утекает в команду сервера",
        )

    def test_text_SkippedAndStored_AddUp(self):
        path = self.write_log(
            "plain.log",
            [
                "[09Nov2026 11:00:00.000] [Server thread/INFO] [net.minecraft.Foo/]: обычная строка",
                "[09Nov2026 11:00:01.000] [Server thread/WARN] [net.minecraft.Foo/]: предупреждение",
            ],
        )
        counters = loader.ingest_text(self.conn, path)
        self.conn.commit()
        self.assertEqual(2, counters["lines"])
        self.assertEqual(1, counters["skipped"])
        self.assertEqual(1, counters["messages"])
        self.assertEqual(1, counters["stored"])
        self.assertEqual(counters["lines"], counters["stored"] + counters["merged"] + counters["skipped"])
        self.assertEqual({"net.minecraft.Foo/": 1}, counters["skipped_by_logger"])
        self.assertTrue(counters["skipped_samples"])

    def test_text_LevelFilter_LeavesOnlyRequestedLevel(self):
        path = self.write_log(
            "mixed.log",
            [
                "[09Nov2026 11:00:00.000] [Server thread/INFO] [com.theminespirit.Board/]: [board] "
                "ASSIGNED задание=t-1 кейс=GATHER_MATERIALS попыток=1 выбор=spirit-1",
                "[09Nov2026 11:00:01.000] [Server thread/DEBUG] [com.theminespirit.Move/]: подробности шага",
            ],
        )
        counters = loader.ingest_text(self.conn, path, levels={"DEBUG"})
        self.conn.commit()
        self.assertEqual(2, counters["lines"])
        self.assertEqual(1, counters["skipped"])
        self.assertEqual(1, counters["messages"])
        self.assertEqual({}, counters["typed"])

    def test_text_BrokenRecord_IsStoredAsErrorAndDoesNotAbort(self):
        path = self.write_log(
            "broken.log",
            ["[09Nov2026 11:00:00.000] [Server thread/INFO] [com.theminespirit.Board/]: [board] сломано"],
        )
        bad = {"type": "block_change", "ts": 1_700_000_000_000, "tick": 0, "build": "x", "source": "import"}
        with mock.patch.object(loader, "parse_text", return_value=(bad, True)):
            counters = loader.ingest_text(self.conn, path)
        self.conn.commit()
        self.assertEqual(1, counters["lines"])
        self.assertEqual(1, counters["errors"])
        self.assertEqual(1, counters["stored"])
        self.assertEqual(0, counters["messages"])
        self.assertEqual(counters["lines"], counters["stored"] + counters["merged"] + counters["skipped"])
        self.assertEqual(1, self.count("messages"))
        self.assertIn("нет обязательного поля", counters["error_samples"][0])

    def test_text_GzipWithoutFinalNewline_IsImported(self):
        path = self.dir / "tail.log.gz"
        with gzip.open(path, "wb") as stream:
            stream.write(
                b"[09Nov2026 11:00:00.000] [Server thread/WARN] [net.minecraft.Foo/]: \xd1\x85\xd0\xb2\xd0\xbe\xd1\x81\xd1\x82"
            )
        counters = loader.ingest_text(self.conn, path)
        self.conn.commit()
        self.assertEqual(1, counters["lines"])
        self.assertEqual(0, counters["tail"])
        self.assertEqual(1, counters["stored"])
        self.assertEqual(1, counters["messages"])

    def test_import_DeleteVerified_RemovesGzAndKeepsLatest(self):
        logs = self.dir / "logs"
        logs.mkdir()
        old = logs / "old.log.gz"
        with gzip.open(old, "wb") as stream:
            stream.write(
                b"[09Nov2026 11:00:00.000] [Server thread/WARN] [net.minecraft.Foo/]: warning\n"
            )
        latest = logs / "latest.log"
        latest.write_text(
            "[09Nov2026 11:00:01.000] [Server thread/WARN] [net.minecraft.Foo/]: warning\n",
            encoding="utf-8",
        )
        report = self.dir / "report.json"
        result = subprocess.run(
            [
                sys.executable, str(LOGDB / "import_legacy.py"),
                "--db", str(self.db), "--logs", str(logs),
                "--exclude", "latest.log", "--report", str(report),
                "--delete-verified",
            ],
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse(old.exists(), "сверенный архив должен удаляться")
        self.assertTrue(latest.exists(), "latest.log не трогаем")
        data = json.loads(report.read_text(encoding="utf-8"))
        self.assertEqual(1, len(data["files"]))
        self.assertTrue(data["files"][0]["verified"])
        self.assertTrue(data["files"][0]["deleted"])
        self.assertEqual(1, data["files"][0]["db_rows"])


class ModVersionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.logs = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def write_debug(self, *versions):
        lines = []
        for version in versions:
            lines.append(
                "[09Nov2026 22:45:03.191] [main/DEBUG] [net.neoforged.fml.loading.moddiscovery.ModFileInfo/LOADING]: "
                "Found valid mod file theminespirit-%s.jar with {theminespirit} mods - versions {%s}" % (version, version)
            )
        (self.logs / "debug.log").write_text("\n".join(lines) + "\n", encoding="utf-8")

    def test_running_TakesLastLoadFromDebugLog(self):
        self.write_debug("0.1.3454", "0.1.3480")

        self.assertEqual(
            "0.1.3480",
            mod_version.running(str(self.logs)),
            "После перезапуска берётся последняя запись NeoForge: иначе сервер «крутит» старую сборку, "
            "пока в mods/ уже новая",
        )

    def test_running_MissingLog_NamesTheCause(self):
        with self.assertRaises(SystemExit) as raised:
            mod_version.running(str(self.logs))

        self.assertIn("debug.log", str(raised.exception.code), "Без журнала версия не выдумывается, причина названа")

    def test_running_NoLoadRecord_NamesVersions(self):
        (self.logs / "debug.log").write_text(
            "[10Nov2026 02:45:03.191] [main/INFO] [net.neoforged.fml.loading.moddiscovery.ModDiscoverer/SCAN]: "
            "Found mod file \"theminespirit-0.1.3480.jar\"\n",
            encoding="utf-8",
        )

        with self.assertRaises(SystemExit) as raised:
            mod_version.running(str(self.logs))

        self.assertIn("versions", str(raised.exception.code), "Имя jar в mods/ не доказывает загрузку")

    def test_liveCopy_ReadsDebugLog_WithoutBash(self):
        copy_logs = self.logs / "logs"
        copy_logs.mkdir()
        (copy_logs / "debug.log").write_text(
            "[09Nov2026 22:45:03.191] [main/DEBUG] [net.neoforged.fml.loading.moddiscovery.ModFileInfo/LOADING]: "
            "Found valid mod file theminespirit-0.1.3500.jar with {theminespirit} mods - versions {0.1.3500}\n",
            encoding="utf-8",
        )
        calls = []
        real = subprocess.run

        def spy(*arguments, **keywords):
            calls.append(arguments[0] if arguments else keywords.get("args"))
            return real(*arguments, **keywords)

        with mock.patch.object(live.subprocess, "run", side_effect=spy), redirect_stdout(io.StringIO()):
            target = live.Target(force_main=True)
            target.copy = str(self.logs)
            got = target.running_mod_version()
            self.assertEqual(
                "0.1.3500",
                got,
                "Версия копии читается из debug.log: имя jar доказывает установку, а не загрузку",
            )
            self.assertTrue(
                all("bash" not in command for command in calls),
                "live.py не зовёт bash для копии: на машине, где bash = WSL, пути Windows ему чужие и проверка падает",
            )


if __name__ == "__main__":
    unittest.main()