import dataclasses
import json
import tempfile
import unittest
from pathlib import Path

import config as config_module
from smoke import run_smoke
from tests.fake_rest import FIXTURES
from tests.scripted_brain import ScriptedBrain

PLAN = {"reason": "Рядом лес.", "goal": "срубить деревья", "plan": {"type": "script", "script": "chop_tree", "params": {"count": 3}}}
BAD = {"reason": "хочу залить", "goal": "залить", "plan": {"type": "script", "script": "fill", "params": {"block": "stone", "x": 1, "y": 1, "z": 1}}}
SKIP = {"reason": "нечего делать", "skip": True}


class SmokeTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.home = Path(self.tmp.name) / "home"
        self.config = dataclasses.replace(config_module.from_env({"STRATEGIST_HOME": str(self.home)}))

    def run_with(self, script, default=None, runs=2):
        brain = ScriptedBrain("opencode/test-free", script=script, default=default)
        report = run_smoke(self.config, brain, FIXTURES, runs=runs, out=None)
        return brain, report, report.read_text(encoding="utf-8")

    def test_smoke_TenRuns_ReportHasTableSharesAndTiming(self):
        script = [{"json": PLAN}] * 7 + [{"json": SKIP}, {"text": "проза"}, {"json": BAD}]
        brain, report, text = self.run_with(script)
        self.assertEqual(10, len(brain.calls), "5 мест × 2 прогона")
        self.assertTrue(report.name.startswith("smoke-") and report.suffix == ".md")
        self.assertEqual(self.home, report.parent, "отчёт лежит в STRATEGIST_HOME")
        for needle in ("opencode/test-free", "forest", "farm", "mine", "empty", "full", "chop_tree", "нечего делать"):
            self.assertIn(needle, text, f"в отчёте должно быть «{needle}»")
        self.assertIn("прошли plan_check: 7 из 10 (70 %)", text)
        self.assertIn("валидных ответов (план или skip): 8 из 10 (80 %)", text)
        self.assertIn("Время ответа", text)

    def test_smoke_ValidShareBelow80_ReportSuggestsEnglishPromptForMainAgent(self):
        script = [{"json": PLAN}] * 6 + [{"text": "проза"}] * 4
        _, _, text = self.run_with(script)
        self.assertIn("ниже 80 %", text)
        self.assertIn("английск", text)
        self.assertIn("решает главный агент", text)

    def test_smoke_ValidShare80OrMore_NoSuggestion(self):
        _, _, text = self.run_with([{"json": PLAN}] * 10)
        self.assertNotIn("ниже 80 %", text)

    def test_smoke_FrequentErrors_ListedWithCounts(self):
        script = [{"json": PLAN}] * 5 + [{"json": BAD}] * 3 + [{"text": "проза"}] * 2
        _, _, text = self.run_with(script)
        self.assertIn("белого списка", text, "частые ошибки: текст отказа plan_check")
        self.assertIn("×3", text)

    def test_smoke_ProviderUnavailableThreeInRow_StopsEarlyAndSaysSo(self):
        brain, _, text = self.run_with([], default={"unavailable": "provider", "text": "Free usage exceeded"})
        self.assertEqual(3, len(brain.calls), "после трёх подряд недоступностей прогон прерывается")
        self.assertIn("прерван", text)
        self.assertIn("Free usage exceeded", text)

    def test_smoke_ServerPlanCheck_CountedWhenFakeRejects(self):
        _, _, text = self.run_with([{"json": PLAN}] * 10)
        self.assertIn("прошли POST /v1/plan фейка: 10 из 10", text)


if __name__ == "__main__":
    unittest.main()
