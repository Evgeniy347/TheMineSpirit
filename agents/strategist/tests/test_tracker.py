"""Расписание опросов задач (tracker.py): без сети и без сценария целиком — клиент-заглушка и подделанные часы."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import config as config_module
from guard import Guard
from journal import Journal
from runtime import Runtime
from state import StateStore
from tracker import Tracker
from tests.helpers import FakeClock

TASK_ID = "t-1"


class StubClient:
    """Клиент без сети: отдаёт один и тот же ответ задачи и помнит, что у него просили."""

    def __init__(self, view: dict):
        self.view = view
        self.gets: list[str] = []

    def get(self, path: str) -> dict:
        self.gets.append(path)
        return dict(self.view)


class TrackerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        env = {"STRATEGIST_HOME": str(Path(self.tmp.name) / "home"), "STRATEGIST_MODE": "live"}
        self.config = config_module.from_env(env)
        self.clock = FakeClock()
        self.journal = Journal(self.config.journal_path, self.clock, self.config.journal_max_bytes,
                               self.config.journal_files)
        self.store = StateStore(self.config.state_path, self.clock)
        self.state = self.store.load(self.journal).state
        self.client = StubClient({"task_id": TASK_ID, "state": "succeeded", "progress": None, "result": None,
                                  "error": None})
        self.rt = Runtime(config=self.config, client=self.client, journal=self.journal,
                          guard=Guard(self.config, self.state.get("guard")), store=self.store, state=self.state,
                          clock=self.clock, out=None)
        self.tracker = Tracker(self.rt)

    def add_task(self, tracker: Tracker | None = None) -> None:
        (tracker or self.tracker).add(TASK_ID, "forest", "срубить деревья", "r-1", "fp-1", 1, now=self.clock.now())

    def test_poll_BeforeTaskPollDeadline_DoesNotAskTask(self):
        self.add_task()
        self.assertEqual(0, self.tracker.poll(), "до task_poll опрашивать задачу рано")
        self.assertEqual([], self.client.gets, "расписание опроса живёт в памяти: раньше срока GET /v1/tasks не уходит")
        self.clock.advance(self.config.task_poll)
        self.assertEqual(1, self.tracker.poll(), "по наступлении срока задача опрашивается и завершается")

    def test_poll_FinishedTask_LeavesNoScheduleEntry(self):
        self.add_task()
        self.clock.advance(self.config.task_poll)
        self.assertEqual(1, self.tracker.poll())
        self.assertEqual({}, self.state["active"], "завершённая задача убрана из активных")
        self.assertEqual({}, self.tracker._next_poll,
                         "расписание не помнит завершённые задачи: словарь живёт вечно в процессе и иначе растёт "
                         "с каждой задачей")


if __name__ == "__main__":
    unittest.main()
