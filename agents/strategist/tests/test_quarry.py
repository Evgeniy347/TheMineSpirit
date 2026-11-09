import tempfile
import unittest

import quarry
from tests.scenario_runner import ScenarioRun


def bot():
    return [{"id": "b1", "world": "forest", "state": "busy"}]


def candidate(x, reserve, path):
    return {"point": {"x": x, "y": 64, "z": 0}, "radius": 4, "reserve": reserve, "path": path,
            "value": reserve * 1000 // max(1, path), "biome": "minecraft:plains"}


class QuarryRuleTests(unittest.TestCase):
    def test_weak_sites_LowEmptyUnset_SelectedForDirtAndSand(self):
        view = {"sites": [
            {"resource": "dirt", "state": "low", "remaining_percent": 7},
            {"resource": "sand", "state": "active", "remaining_percent": 80},
            {"resource": "snow", "state": "empty", "remaining_percent": 0},
            {"resource": "sand", "state": "unset", "remaining_percent": 0},
        ]}

        self.assertEqual(["dirt", "sand"], [entry["resource"] for entry in quarry.weak_sites(view)],
                         "решение нужно только земле и песку в состоянии low/empty/unset")

    def test_best_candidate_HighestValueWins(self):
        best = quarry.best_candidate([candidate(100, 100, 100), candidate(200, 50, 10)])

        self.assertEqual(200, best["point"]["x"],
                         "близкие 50 блоков выгоднее далёких 100: правило — запас на длину пути")

    def test_best_candidate_NoReserve_None(self):
        self.assertIsNone(quarry.best_candidate([candidate(100, 0, 10)]),
                          "область без запаса местом добычи не становится")


class QuarryCycleTests(unittest.TestCase):
    def test_cycle_LowDirt_PostsBestSiteWithoutModel(self):
        scenario = {"name": "quarry-low", "bots": bot(),
                    "steps": [
                        {"fake": {"quarry": {
                            "sites": [{"resource": "dirt", "state": "low", "remaining_percent": 7},
                                      {"resource": "sand", "state": "active", "remaining_percent": 80}],
                            "candidates": {"dirt": [candidate(100, 100, 100), candidate(200, 50, 10)]}}}},
                        {"cycle": True}],
                    "expect": [{"quarry_posts": 1},
                               {"quarry_body": {"resource": "dirt", "x": 200, "by": "strategist"}},
                               {"brain_calls": 0}]}
        with tempfile.TemporaryDirectory() as tmp:
            run = ScenarioRun(scenario, tmp)
            try:
                run.run()
            finally:
                run.close()
            decisions = [event for event in run.events() if event["event"] == "quarry_decision"]
            self.assertEqual(1, len(decisions), "решение о месте добычи записано в журнал")
            self.assertFalse(decisions[0]["dry"], "в live место ставится по-настоящему")

    def test_cycle_NoCandidates_NoPost(self):
        scenario = {"name": "quarry-none", "bots": bot(),
                    "steps": [
                        {"fake": {"quarry": {
                            "sites": [{"resource": "dirt", "state": "empty", "remaining_percent": 0},
                                      {"resource": "sand", "state": "active", "remaining_percent": 80}],
                            "candidates": {"dirt": []}}}},
                        {"cycle": True}],
                    "expect": [{"quarry_posts": 0}, {"journal_has": {"event": "quarry_none"}}]}
        with tempfile.TemporaryDirectory() as tmp:
            run = ScenarioRun(scenario, tmp)
            try:
                run.run()
            finally:
                run.close()

    def test_cycle_Dry_DoesNotPost(self):
        scenario = {"name": "quarry-dry", "bots": bot(), "config": {"STRATEGIST_MODE": "dry"},
                    "steps": [
                        {"fake": {"quarry": {
                            "sites": [{"resource": "dirt", "state": "unset", "remaining_percent": 0},
                                      {"resource": "sand", "state": "active", "remaining_percent": 80}],
                            "candidates": {"dirt": [candidate(100, 100, 100)]}}}},
                        {"cycle": True}],
                    "expect": [{"quarry_posts": 0},
                               {"journal_has": {"event": "quarry_decision", "dry": True}}]}
        with tempfile.TemporaryDirectory() as tmp:
            run = ScenarioRun(scenario, tmp)
            try:
                run.run()
            finally:
                run.close()

    def test_cycle_RouteMissing_Quiet(self):
        with tempfile.TemporaryDirectory() as tmp:
            run = ScenarioRun({"name": "quarry-missing", "bots": bot(), "steps": [{"cycle": True}],
                               "expect": [{"quarry_posts": 0}]}, tmp)
            try:
                run.fake.quarry_route = False
                run.run()
            finally:
                run.close()


if __name__ == "__main__":
    unittest.main()
