import copy
import json
import random
import tempfile
import time
import unittest
from unittest import mock
from datetime import datetime
from pathlib import Path

from tests.fake_rest import FakeRest
from tests.scenario_runner import ScenarioRun, load_scenarios, scripts_in

SCENARIOS = Path(__file__).resolve().parent / "scenarios"
HOUR = 3600
NO_SLEEP = "в сценарии вызван настоящий time.sleep: часы подделаны, ожидание обязано идти через FakeClock"
NO_HTTP = "сценарии идут на InProcessClient внутри процесса: HTTP-сервер фейка (сокет, поток, SSL) им не нужен"
REQUIRED_TOPICS = (
    "idle-valid-plan", "all-busy", "skip", "invalid-script-not-in-catalog", "invalid-param-out-of-bounds",
    "invalid-script-outside-whitelist", "invalid-plan-too-large", "not-json", "fenced-json-accepted",
    "retry-fixed-plan", "retry-still-invalid", "server-422", "post-409", "became-busy", "eleventh-post",
    "failed-plan-cooldown", "three-failures", "unchanged-hash", "provider-down", "silence", "restart-with-active-task",
    "tasks-route-missing", "dry-mode-never-posts", "pause-file", "task-404", "task-timeout", "two-bots", "global-brain-call-limit",
    "prompt-injection", "cancelled-human-is-not-failure", "count-only-diff",
)


class ScenarioTableTests(unittest.TestCase):
    def test_table_HasAtLeast24ScenariosCoveringEveryRequiredTopic(self):
        names = [s["name"] for s in load_scenarios(SCENARIOS)]
        self.assertGreaterEqual(len(names), 24, "сценариев должно быть не меньше 24")
        joined = " ".join(names)
        missing = [topic for topic in REQUIRED_TOPICS if topic not in joined]
        self.assertEqual([], missing, f"нет обязательных сценариев чеклиста 22 п. 11: {missing}")

    def test_scenarios_EachPasses(self):
        started = time.monotonic()
        for scenario in load_scenarios(SCENARIOS):
            with self.subTest(scenario=scenario["name"]):
                with tempfile.TemporaryDirectory() as tmp, mock.patch("time.sleep", side_effect=AssertionError(NO_SLEEP)), \
                        mock.patch.object(FakeRest, "start", side_effect=AssertionError(NO_HTTP)):
                    run = ScenarioRun(scenario, Path(tmp))
                    try:
                        run.run()
                    finally:
                        run.close()
        self.assertLess(time.monotonic() - started, 10,
                       "набор сценариев идёт на подделанных часах и без HTTP (чеклист 22, п. 11: весь набор ≤ 10 с): "
                       "замедление означает, что в него вернулся настоящий сокет или ожидание")


PLANS = [
    {"type": "script", "script": "chop_tree", "params": {"count": 3}},
    {"type": "script", "script": "harvest_crop", "params": {"crop": "wheat", "radius": 8}},
    {"type": "script", "script": "mine_vein", "params": {"ore": "iron", "count": 2}},
    {"type": "sequence", "steps": [{"type": "script", "script": "goto", "params": {"x": 1, "y": 64, "z": 1}},
                                    {"type": "script", "script": "gather", "params": {"block": "minecraft:stone", "count": 8}}]},
    {"type": "script", "script": "wait", "params": {"ticks": 100}},
]
BAD_PLANS = [
    {"type": "script", "script": "fill", "params": {"block": "stone", "x": 1, "y": 1, "z": 1}},
    {"type": "script", "script": "chop_tree", "params": {"count": 5000}},
    {"type": "script", "script": "teleport", "params": {}},
    {"type": "loop", "steps": []},
]


def random_entry(rng):
    roll = rng.random()
    if roll < 0.5:
        plan = rng.choice(PLANS)
        return {"json": {"reason": "случайное решение", "goal": f"цель {rng.randint(0, 3)}", "plan": plan}}
    if roll < 0.62:
        return {"json": {"reason": "случайное решение", "goal": "плохая цель", "plan": rng.choice(BAD_PLANS)}}
    if roll < 0.72:
        return {"json": {"reason": "ждать", "skip": True}}
    if roll < 0.8:
        return {"text": "проза без JSON"}
    if roll < 0.86:
        return {"fenced": {"reason": "в ограждении", "goal": "цель", "plan": rng.choice(PLANS)}}
    if roll < 0.92:
        return {"unavailable": rng.choice(["provider", "silence"]), "text": "Rate limit exceeded"}
    return {"error": "мусор на выходе"}


def random_scenario(seed):
    rng = random.Random(seed)
    bots = [{"id": f"s{i}", "world": rng.choice(["forest", "farm", "mine", "empty", "full"]), "tweak": rng.randint(0, 3)}
            for i in range(rng.randint(1, 3))]
    fate = rng.choice([{"outcome": "succeeded", "after_polls": rng.randint(1, 3)},
                       {"outcome": "failed", "after_polls": 1, "error": "не вышло"}, {"outcome": "never"}])
    steps = []
    for _ in range(24):
        roll = rng.random()
        bot = rng.choice(bots)["id"]
        if roll < 0.35:
            steps.append({"cycle": True})
        elif roll < 0.65:
            steps.append({"advance": rng.choice([10, 60, 300, 300, 900, 1800])})
        elif roll < 0.75:
            steps.append({"fake": {"set_world": {"bot": bot, "world": rng.choice(["forest", "farm", "mine"]), "tweak": rng.randint(0, 5)}}})
        elif roll < 0.82:
            steps.append({"fake": {"set_state": {"bot": bot, "state": rng.choice(["idle", "busy", "dead"])}}})
        elif roll < 0.86:
            steps.append({"pause_file": rng.random() < 0.5})
        elif roll < 0.9:
            steps.append({"restart": True})
        elif roll < 0.94:
            steps.append({"fake": {"task_post_errors": [[409, "conflict"]]}})
        elif roll < 0.97:
            steps.append({"fake": {"forget_tasks": True}})
        else:
            steps.append({"fake": {"plan_error": rng.choice([None, "Скрипт не прошёл проверку мода"])}})
    return {"name": f"random-{seed}", "bots": bots, "fates": {"*": fate}, "config": {"STRATEGIST_MODE": rng.choice(["live", "live", "dry"])},
            "brain": {"script": [random_entry(rng) for _ in range(80)], "default": {"json": {"reason": "ждать", "skip": True}}},
            "steps": steps}


def parse_ts(text):
    return datetime.fromisoformat(text).timestamp()


class ScenarioPropertyTests(unittest.TestCase):
    def test_property_RandomScenarios_NeverBreakLimitsOrWhitelist(self):
        started = time.monotonic()
        total_posts = 0
        for seed in range(25):
            scenario = random_scenario(seed)
            with tempfile.TemporaryDirectory() as tmp, mock.patch("time.sleep", side_effect=AssertionError(NO_SLEEP)), \
                    mock.patch.object(FakeRest, "start", side_effect=AssertionError(NO_HTTP)):
                run = ScenarioRun(copy.deepcopy(scenario), Path(tmp))
                try:
                    run.run()
                    total_posts += self.assert_invariants(run, scenario, seed)
                finally:
                    run.close()
        self.assertGreater(total_posts, 10, "случайные сценарии должны реально ставить задачи, иначе свойство проверяет пустоту")
        self.assertLess(time.monotonic() - started, 10,
                       "свойство идёт внутри процесса на подделанных часах (чеклист 22, п. 11: весь набор ≤ 10 с): "
                       "медленный прогон означает настоящий сокет, ожидание или лишние записи на диск")

    def assert_invariants(self, run, scenario, seed):
        posts = [r for r in run.task_posts() if r["status"] in (202, "response_lost")]
        if scenario["config"]["STRATEGIST_MODE"] == "dry":
            self.assertEqual([], run.task_posts(), f"seed={seed}: в dry ушёл POST tasks")
        allowed = set(json.loads(run.config.policy_path.read_text(encoding="utf-8"))["allowed_scripts"])
        by_bot: dict[str, list[float]] = {}
        for request in run.task_posts():
            used = scripts_in(request["body"].get("plan"))
            self.assertTrue(used <= allowed, f"seed={seed}: ушёл скрипт вне белого списка {sorted(used - allowed)}")
        created = {}
        for request in posts:
            bot = request["path"].split("/")[3]
            created.setdefault(request["body"]["request_id"], (bot, request["fake_t"]))
        for bot_id in {bot for bot, _ in created.values()}:
            times = sorted(t for b, t in created.values() if b == bot_id)
            for start in times:
                in_hour = [t for t in times if start - HOUR < t <= start]
                self.assertLessEqual(len(in_hour), 10, f"seed={seed}, дух {bot_id}: за скользящий час {len(in_hour)} новых задач (>10)")
            for earlier, later in zip(times, times[1:]):
                self.assertGreaterEqual(later - earlier, 60, f"seed={seed}, дух {bot_id}: задачи ближе 60 с друг к другу")
        events = run.events()
        failed = [(e["bot"], parse_ts(e["ts"]), e.get("fingerprint")) for e in events
                  if e["event"] == "task_done" and e.get("outcome") in ("failed", "lost", "timeout")]
        for bot, when, fingerprint in failed:
            for event in events:
                if event["event"] == "posted" and event["bot"] == bot and event.get("fingerprint") == fingerprint \
                        and not event.get("recovered") and when < parse_ts(event["ts"]) < when + 1800:
                    self.fail(f"seed={seed}: провалившийся план повторно ушёл через {parse_ts(event['ts']) - when:.0f} с (<1800)")
        return len(posts)


if __name__ == "__main__":
    unittest.main()
