import contextlib
import io
import json
import os
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path
from unittest import mock

import strategist

APP = Path(__file__).resolve().parent.parent
FIXTURES = APP / "tests" / "fixtures"
PLAN_REPLY = {"reason": "Рядом лес.", "goal": "срубить деревья",
              "plan": {"type": "script", "script": "chop_tree", "params": {"count": 3}}}


class MainTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)
        self.home = self.dir / "home"
        self.brain = self.dir / "brain.sh"
        self.brain.write_text("#!/usr/bin/env bash\ncat > /dev/null\nprintf '%s' '" + json.dumps(PLAN_REPLY, ensure_ascii=False) + "'\n",
                              encoding="utf-8")
        self.brain.chmod(0o755)
        self.env = {"STRATEGIST_HOME": str(self.home), "STRATEGIST_BRAIN": "command", "STRATEGIST_BRAIN_COMMAND": str(self.brain),
                    "STRATEGIST_MODE": "dry", "STRATEGIST_TASK_POLL": "30", "STRATEGIST_REST_PER_SECOND": "20"}

    def run_main(self, args, **env):
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.dict(os.environ, {**self.env, **env}, clear=False), contextlib.redirect_stdout(out), \
                contextlib.redirect_stderr(err):
            code = strategist.main(args)
        return code, out.getvalue(), err.getvalue()

    def events(self):
        path = self.home / "journal.jsonl"
        return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()] if path.exists() else []

    def test_once_Fixtures_DryJournalsWouldPostForEveryIdleBot(self):
        code, out, err = self.run_main(["once", "--fixtures", str(FIXTURES)])
        self.assertEqual(0, code, f"once --fixtures должен завершаться кодом 0; stderr: {err}")
        posted = [e for e in self.events() if e["event"] == "posted"]
        self.assertEqual(5, len(posted), "по записи «отправил бы» на каждого из 5 духов из fixtures")
        self.assertTrue(all(e["dry"] and e["outcome"] == "would_post" for e in posted))
        self.assertIn("Фейковый REST", out)

    def test_once_BadConfig_ExitsTwoNamingVariable(self):
        code, _, err = self.run_main(["once", "--fixtures", str(FIXTURES)], STRATEGIST_INTERVAL="1")
        self.assertEqual(2, code)
        self.assertIn("STRATEGIST_INTERVAL", err)

    def test_liveWithoutConfirmation_Refused(self):
        code, _, err = self.run_main(["once", "--fixtures", str(FIXTURES)], STRATEGIST_MODE="live")
        self.assertEqual(2, code, "live без STRATEGIST_CONFIRM_LIVE=1 запрещён")
        self.assertIn("согласи", err)
        self.assertFalse((self.home / "journal.jsonl").exists(), "отказ происходит до любых действий")

    def test_secondInstance_RefusedByLock(self):
        lock = strategist.acquire_lock(self.home)
        self.assertIsNotNone(lock)
        try:
            code, _, err = self.run_main(["once", "--fixtures", str(FIXTURES)])
        finally:
            lock.close()
        self.assertEqual(3, code)
        self.assertIn("Другой экземпляр", err)
        code, _, _ = self.run_main(["once", "--fixtures", str(FIXTURES)])
        self.assertEqual(0, code, "после освобождения замка запуск возможен")

    def test_status_ShowsModeCountersActiveTasksAndLastFailure(self):
        self.run_main(["once", "--fixtures", str(FIXTURES)])
        code, out, _ = self.run_main(["status"])
        self.assertEqual(0, code)
        for needle in ("агент: не запущен", "режим: dry", "последний цикл:", "решений 5", "в dry 5", "активных задач: 0",
                       "последний сбой: нет", "PAUSE"):
            self.assertIn(needle, out, f"status должен показывать «{needle}»")

    def test_status_WithoutHome_DoesNotCrash(self):
        code, out, _ = self.run_main(["status"], STRATEGIST_HOME=str(self.dir / "nowhere"))
        self.assertEqual(0, code)
        self.assertIn("последний цикл: нет", out)

    def test_run_Sigterm_StopsCleanlyAndKeepsTasks(self):
        env = {**os.environ, **self.env, "STRATEGIST_MODE": "live", "STRATEGIST_CONFIRM_LIVE": "1"}
        process = subprocess.Popen([sys.executable, str(APP / "strategist.py"), "run", "--fixtures", str(FIXTURES)], env=env,
                                   cwd=APP, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline and not [e for e in self.events() if e["event"] == "posted"]:
                time.sleep(0.2)
            self.assertTrue([e for e in self.events() if e["event"] == "posted"], "за 30 с стратег не поставил ни одной задачи")
            process.send_signal(signal.SIGTERM)
            out, err = process.communicate(timeout=30)
        finally:
            if process.poll() is None:
                process.kill()
        self.assertEqual(0, process.returncode, f"SIGTERM — штатное завершение; stderr: {err}")
        self.assertIn("остановлен корректно", out)
        state = json.loads((self.home / "state.json").read_text(encoding="utf-8"))
        self.assertGreaterEqual(len(state["active"]), 1, "поставленные задачи не отменяются при остановке")
        self.assertEqual([], [e for e in self.events() if e["event"] == "task_done" and e.get("outcome") == "cancelled"])


if __name__ == "__main__":
    unittest.main()
