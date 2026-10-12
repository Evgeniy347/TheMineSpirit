"""Остановка стратега: прерывание вызова мозга (BrainStopped), смерть группы процессов, опрос задач между вызовами."""

import json
import os
import signal
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from pathlib import Path

import config as config_module
from brain import BrainStopped, CommandBrain, FallbackBrain, OpencodeBrain
from tests.scenario_runner import ScenarioRun

APP = Path(__file__).resolve().parent.parent
FIXTURES = APP / "tests" / "fixtures"
PLAN_REPLY = {"reason": "Рядом лес.", "goal": "срубить деревья",
              "plan": {"type": "script", "script": "chop_tree", "params": {"count": 3}}}


def dead(pid: int, timeout: float = 3.0) -> bool:
    deadline = time.monotonic() + timeout
    while True:
        try:
            os.kill(pid, 0)
        except ProcessLookupError:
            return True
        if time.monotonic() >= deadline:
            return False
        time.sleep(0.05)


class StopCallTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)
        self.state = self.dir / "fake.state"
        self.pid_path = Path(str(self.state) + ".pid")
        self.child_path = Path(str(self.state) + ".childpid")
        self.config = config_module.from_env({
            "STRATEGIST_HOME": str(self.dir / "home"),
            "STRATEGIST_OPENCODE_BIN": str(APP / "tests" / "fake_opencode.sh"),
            "STRATEGIST_BRAIN_TIMEOUT": "5",
        })

    def silent_brain(self) -> OpencodeBrain:
        return OpencodeBrain(self.config, "opencode/test-free", env={"FAKE_STATE": str(self.state), "FAKE_SILENT": "1"})

    def test_stopBeforeCall_DoesNotSpawnProcess(self):
        brain = self.silent_brain()
        brain.request_stop()
        with self.assertRaises(BrainStopped):
            brain.complete("системный", "пользовательский")
        self.assertFalse(self.state.exists(), "после остановки подпроцесс не запускается")

    def test_stopDuringHangingCall_KillsGroupAndRaisesBrainStopped(self):
        brain = self.silent_brain()

        def stopper():
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline and not self.pid_path.exists():
                time.sleep(0.01)
            brain.request_stop()

        thread = threading.Thread(target=stopper, daemon=True)
        started = time.monotonic()
        thread.start()
        with self.assertRaises(BrainStopped):
            brain.complete("системный", "пользовательский")
        elapsed = time.monotonic() - started
        thread.join(timeout=5)
        self.assertLess(elapsed, 3, "после остановки вызов прерывается сразу, а не ждёт тайм-аута 5 с")
        self.assertTrue(self.pid_path.exists(), "вызов мозга успел начаться")
        self.assertTrue(dead(int(self.child_path.read_text())),
                        "спящий дочерний процесс той же группы убит вместе с мозгом")

    def test_stopDuringHangingCommand_KillsGroupAndRaisesBrainStopped(self):
        marker = self.dir / "started"
        config = config_module.from_env({
            "STRATEGIST_HOME": str(self.dir / "cmd-home"),
            "STRATEGIST_BRAIN": "command",
            "STRATEGIST_BRAIN_COMMAND": f"bash -c 'echo started > {marker}; sleep 60'",
            "STRATEGIST_BRAIN_TIMEOUT": "5",
        })
        brain = CommandBrain(config, config.brain_command, "command")

        def stopper():
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline and not marker.exists():
                time.sleep(0.01)
            brain.request_stop()

        thread = threading.Thread(target=stopper, daemon=True)
        started = time.monotonic()
        thread.start()
        with self.assertRaises(BrainStopped):
            brain.complete("системный", "пользовательский")
        elapsed = time.monotonic() - started
        thread.join(timeout=5)
        self.assertLess(elapsed, 3, "после остановки команда прерывается сразу, а не ждёт тайм-аута 5 с")
        self.assertTrue(marker.exists(), "команда мозга успела запуститься")

    def test_stop_ForwardsToPrimaryAndFallback(self):
        seen = []

        class Dummy:
            model = "dummy"

            def __init__(self, name: str):
                self.name = name

            def request_stop(self):
                seen.append(self.name)

        FallbackBrain(Dummy("primary")).request_stop()
        FallbackBrain(Dummy("primary"), Dummy("fallback")).request_stop()
        self.assertEqual(["primary", "primary", "fallback"], seen,
                         "остановка обязана дойти и до запасной модели, иначе её вызов дожидается тайм-аута")


class StoppedBrain:
    model = "opencode/stopped-free"

    def __init__(self):
        self.complete_calls = 0
        self.probe_calls = 0

    def verify(self):
        return None

    def request_stop(self):
        return None

    def complete(self, system: str, user: str):
        self.complete_calls += 1
        raise BrainStopped("вызов прерван: стратег останавливается")

    def probe(self):
        self.probe_calls += 1
        raise BrainStopped("проба прервана: стратег останавливается")


class StopCycleTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)

    def run_world(self) -> ScenarioRun:
        scenario = {"name": "stop-cycle",
                    "brains": {"primary": {"default": {"json": {"reason": "ждать", "skip": True}}}},
                    "bots": [{"id": "b0", "world": "forest"}, {"id": "b1", "world": "farm"}],
                    "steps": []}
        run = ScenarioRun(scenario, Path(self.tmp.name))
        self.addCleanup(run.close)
        run.strategist.cycles.brain = StoppedBrain()
        return run

    def test_decide_StoppedCall_EndsCycleWithoutFailureAndSecondBot(self):
        run = self.run_world()
        summary = run.strategist.run_cycle()
        events = run.events()
        self.assertEqual(1, run.strategist.cycles.brain.complete_calls, "прерван вызов у первого духа — и всё")
        self.assertEqual(1, len([e for e in events if e["event"] == "observed"]),
                         "после остановки второй дух не обрабатывается")
        self.assertEqual([], [e for e in events if e["event"] == "brain_failure"],
                         "остановка — не сбой модели: backoff не растёт и интервал не удваивается")
        self.assertTrue(run.strategist.cycles.stop_requested, "цикл сообщает, что остановлен")
        self.assertEqual(0, summary.posted, "после прерванного вызова ничего не отправляется")

    def test_probe_StoppedProbe_EndsCycleBeforeAnyBot(self):
        run = self.run_world()
        run.strategist.cycles.provider.failure()
        summary = run.strategist.run_cycle()
        events = run.events()
        self.assertEqual(1, run.strategist.cycles.brain.probe_calls, "проба провайдера прервана остановкой")
        self.assertEqual(0, run.strategist.cycles.brain.complete_calls)
        self.assertEqual(0, len([e for e in events if e["event"] == "observed"]), "цикл оборван до духов")
        self.assertEqual([], [e for e in events if e["event"] == "brain_failure"],
                         "остановка пробы — не сбой модели")
        self.assertTrue(run.strategist.cycles.stop_requested)
        self.assertEqual(0, summary.posted)


class TimelineBrain:
    """Обёртка сценарного мозга: пишет окна вызовов модели по реальным часам."""

    def __init__(self, inner):
        self.inner = inner
        self.model = inner.model
        self.windows: list[tuple[float, float]] = []
        self.on_call = None

    @property
    def calls(self) -> int:
        return len(self.inner.calls)

    def complete(self, system: str, user: str):
        return self._window(lambda: self.inner.complete(system, user))

    def probe(self):
        return self._window(lambda: self.inner.probe())

    def verify(self):
        return None

    def request_stop(self):
        self.inner.request_stop()

    def _window(self, call):
        start = time.monotonic()
        time.sleep(0.02)  # вызов модели длится не мгновенно: только окно в миллисекунды заметило бы чужой опрос
        if self.on_call is not None:
            self.on_call()
        try:
            return call()
        finally:
            self.windows.append((start, time.monotonic()))


class PollDuringModelCallTests(unittest.TestCase):
    def test_runForever_TaskPollsLandOnlyOutsideModelCallWindows(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        scenario = {"name": "poll-guard",
                    "brains": {"primary": {"script": [{"json": PLAN_REPLY}],
                                           "default": {"json": {"reason": "ждать", "skip": True}}}},
                    "bots": [{"id": "b0", "world": "forest"}, {"id": "b1", "world": "farm"}],
                    "steps": []}
        run = ScenarioRun(scenario, Path(tmp.name))
        self.addCleanup(run.close)
        run.fake.set_fate(None, "never")
        brain = TimelineBrain(run.strategist.cycles.brain)
        run.strategist.cycles.brain = brain
        finished = threading.Event()
        errors: list[str] = []

        def loop():
            try:
                run.strategist.run_forever()
            except BaseException as error:
                errors.append(f"{type(error).__name__}: {error}")
            finally:
                finished.set()

        thread = threading.Thread(target=loop, daemon=True)
        thread.start()
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline and not (brain.calls >= 2 and run.fake.requests_to("GET", "/v1/tasks/")):
            time.sleep(0.02)
        run.strategist.stop()
        thread.join(timeout=10)
        self.assertEqual([], errors, f"run_forever упал: {errors}")
        self.assertTrue(finished.is_set(), "run_forever не завершился за 10 с после stop()")
        self.assertTrue(run.fake.posted_tasks(), "задача поставлена — иначе опроса задач нет и проверка пустая")
        polls = run.fake.requests_to("GET", "/v1/tasks/")
        self.assertTrue(polls, "опрос идёт между вызовами модели — иначе проверка пустая")
        self.assertGreaterEqual(brain.calls, 2, "нужно минимум два вызова модели: окно второго — когда задача уже в работе")
        violations = [request["t"] for request in polls for window in brain.windows if window[0] <= request["t"] <= window[1]]
        self.assertEqual([], violations, f"опрос задач во время вызова модели: {violations}")


class StopProcessTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)
        self.home = self.dir / "home"
        self.state = self.dir / "fake.state"
        self.pid_path = Path(str(self.state) + ".pid")
        self.child_path = Path(str(self.state) + ".childpid")

    def test_sigtermDuringHangingCall_ExitsZeroFastAndGroupDead(self):
        env = {**os.environ,
               "STRATEGIST_HOME": str(self.home),
               "STRATEGIST_MODE": "dry",
               "STRATEGIST_TASK_POLL": "30",
               "STRATEGIST_REST_PER_SECOND": "20",
               "STRATEGIST_BRAIN": "opencode",
               "STRATEGIST_OPENCODE_BIN": str(APP / "tests" / "fake_opencode.sh"),
               "FAKE_STATE": str(self.state),
               "FAKE_SILENT": "1"}
        process = subprocess.Popen([sys.executable, str(APP / "strategist.py"), "run", "--fixtures", str(FIXTURES)],
                                   env=env, cwd=APP, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline and not self.pid_path.exists():
                time.sleep(0.05)
            self.assertTrue(self.pid_path.exists(), "за 30 с вызов мозга не начался")
            started = time.monotonic()
            process.send_signal(signal.SIGTERM)
            out, err = process.communicate(timeout=30)
            elapsed = time.monotonic() - started
        finally:
            if process.poll() is None:
                process.kill()
                process.communicate()
        self.assertEqual(0, process.returncode, f"SIGTERM во время висящего вызова — штатное завершение; stderr: {err}")
        self.assertIn("остановлен корректно", out)
        self.assertLess(elapsed, 15, "SIGTERM прерывает висящий вызов сразу, а не ждёт тайм-аута 240 с")
        self.assertTrue(dead(int(self.child_path.read_text())), "спящий дочерний процесс группы убит")
        events = [json.loads(line) for line in (self.home / "journal.jsonl").read_text(encoding="utf-8").splitlines()]
        self.assertEqual([], [e for e in events if e["event"] == "brain_failure"],
                         "остановка не журналируется как сбой модели")
        self.assertEqual(0, len([e for e in events if e["event"] == "posted"]), "после прерванного вызова ничего не отправлено")


if __name__ == "__main__":
    unittest.main()
