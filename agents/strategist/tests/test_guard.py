import json
import random
import unittest

from config import from_env
from guard import Guard, ProviderHealth, fingerprint, request_id

HOUR = 3600
T0 = 1_800_000_000.0
PLAN_A = {"type": "script", "script": "chop_tree", "params": {"count": 3}}
PLAN_B = {"type": "script", "script": "scan", "params": {}}


def make_guard(**env):
    return Guard(from_env({k: str(v) for k, v in env.items()}))


class FingerprintTests(unittest.TestCase):
    def test_fingerprint_KeyOrderDoesNotMatter(self):
        a = {"type": "script", "script": "goto", "params": {"x": 1, "y": 2, "z": 3}}
        b = {"params": {"z": 3, "y": 2, "x": 1}, "script": "goto", "type": "script"}
        self.assertEqual(fingerprint("цель", a), fingerprint("цель", b), "канонический JSON: порядок ключей не важен")

    def test_fingerprint_DifferentGoalOrPlan_Differs(self):
        self.assertNotEqual(fingerprint("цель", PLAN_A), fingerprint("другая", PLAN_A))
        self.assertNotEqual(fingerprint("цель", PLAN_A), fingerprint("цель", PLAN_B))
        changed = {"type": "script", "script": "chop_tree", "params": {"count": 4}}
        self.assertNotEqual(fingerprint("цель", PLAN_A), fingerprint("цель", changed), "параметры входят в отпечаток")

    def test_requestId_DeterministicUuidPerBotCycleFingerprint(self):
        fp = fingerprint("цель", PLAN_A)
        first = request_id("spirit_1", 7, fp)
        self.assertEqual(first, request_id("spirit_1", 7, fp), "повтор после обрыва обязан дать тот же request_id")
        self.assertEqual(5, len(first.split("-")), "request_id — UUID")
        self.assertNotEqual(first, request_id("spirit_2", 7, fp))
        self.assertNotEqual(first, request_id("spirit_1", 8, fp))
        self.assertNotEqual(first, request_id("spirit_1", 7, fingerprint("цель", PLAN_B)))


class TaskRateTests(unittest.TestCase):
    def test_tasksPerHour_SeventhPostDenied_AndAllowedAfterHourBoundary(self):
        guard = make_guard()
        fp = fingerprint("г", PLAN_A)
        for i in range(6):
            now = T0 + i * 60
            self.assertTrue(guard.allow_post(now, "b", fp).ok, f"задача {i + 1} из 6 должна пройти")
            guard.record_post(now, "b", fp)
        verdict = guard.allow_post(T0 + 360, "b", fp)
        self.assertFalse(verdict.ok, "седьмая задача за час запрещена")
        self.assertIn("6", verdict.reason)
        self.assertFalse(guard.allow_post(T0 + HOUR - 0.001, "b", fp).ok, "ровно до границы часа лимит действует")
        self.assertTrue(guard.allow_post(T0 + HOUR, "b", fp).ok, "на границе часа первая задача выходит из окна")

    def test_tasksPerHour_IsPerBot(self):
        guard = make_guard()
        for i in range(6):
            guard.record_post(T0 + i * 60, "b1", "fp")
        self.assertFalse(guard.allow_post(T0 + 400, "b1", "fp").ok)
        self.assertTrue(guard.allow_post(T0 + 400, "b2", "fp").ok, "лимит считается на духа")

    def test_gapBetweenTasks_BoundarySixtySeconds(self):
        guard = make_guard()
        guard.record_post(T0, "b", "fp")
        self.assertFalse(guard.allow_post(T0 + 59.9, "b", "fp2").ok, "раньше 60 с нельзя")
        self.assertTrue(guard.allow_post(T0 + 60, "b", "fp2").ok, "ровно 60 с можно")

    def test_allowNewTask_ChecksRateAndGapWithoutFingerprint(self):
        guard = make_guard()
        self.assertTrue(guard.allow_new_task(T0, "b").ok)
        for i in range(6):
            guard.record_post(T0 + i * 60, "b", f"fp{i}")
        verdict = guard.allow_new_task(T0 + 360, "b")
        self.assertFalse(verdict.ok, "лимит часа проверяется до наблюдения и модели, без плана")
        self.assertEqual("tasks_hourly", verdict.code)
        self.assertTrue(guard.allow_new_task(T0 + HOUR, "b").ok, "на границе часа лимит снят")
        other = make_guard()
        other.record_post(T0, "b", "fp")
        self.assertEqual("task_gap", other.allow_new_task(T0 + 59, "b").code)
        self.assertTrue(other.allow_new_task(T0 + 60, "b").ok)

    def test_allowNewTask_PausedBot_Denied(self):
        guard = make_guard()
        for i in range(3):
            guard.record_outcome(T0 + i, "b", f"f{i}", "failed")
        self.assertEqual("paused", guard.allow_new_task(T0 + 10, "b").code)

    def test_limits_FollowConfig(self):
        guard = make_guard(STRATEGIST_TASKS_PER_HOUR=2, STRATEGIST_TASK_GAP=0)
        guard.record_post(T0, "b", "a")
        guard.record_post(T0 + 1, "b", "b")
        self.assertFalse(guard.allow_post(T0 + 2, "b", "c").ok, "лимит 2 в час из конфига")


class BrainCallTests(unittest.TestCase):
    def test_hourlyLimit_ThirtyCallsTotal_BoundaryAtHour(self):
        guard = make_guard()
        for i in range(30):
            guard.begin_cycle()
            self.assertTrue(guard.allow_brain_call(T0 + i, f"b{i}", f"h{i}").ok)
            guard.record_brain_call(T0 + i, f"b{i}")
        guard.begin_cycle()
        verdict = guard.allow_brain_call(T0 + 31, "fresh", "hx")
        self.assertFalse(verdict.ok, "31-й вызов модели за час запрещён")
        self.assertIn("30", verdict.reason)
        self.assertFalse(guard.allow_brain_call(T0 + HOUR - 1, "fresh", "hx").ok, "первый вызов (t=0) ещё в окне")
        self.assertTrue(guard.allow_brain_call(T0 + HOUR, "fresh", "hx").ok, "на границе часа первый вызов вышел из окна")

    def test_perBotPerCycle_OneCallPlusOneRetry(self):
        guard = make_guard()
        guard.begin_cycle()
        self.assertTrue(guard.allow_brain_call(T0, "b", "h").ok)
        guard.record_brain_call(T0, "b")
        self.assertFalse(guard.allow_brain_call(T0 + 1, "b", "h").ok, "второй вызов без повтора запрещён")
        self.assertTrue(guard.allow_brain_call(T0 + 1, "b", "h", retry=True).ok, "повтор после отказа проверки разрешён")
        guard.record_brain_call(T0 + 1, "b")
        self.assertFalse(guard.allow_brain_call(T0 + 2, "b", "h", retry=True).ok, "третий вызов за цикл запрещён всегда")
        guard.begin_cycle()
        self.assertTrue(guard.allow_brain_call(T0 + 300, "b", "h2").ok, "новый цикл — новый счёт")

    def test_unchangedHash_ModelNotCalled(self):
        guard = make_guard()
        guard.begin_cycle()
        self.assertTrue(guard.allow_brain_call(T0, "b", "abc").ok)
        guard.record_decision("b", "abc")
        guard.begin_cycle()
        verdict = guard.allow_brain_call(T0 + 300, "b", "abc")
        self.assertFalse(verdict.ok, "hash наблюдения не менялся — модель не вызывается")
        self.assertEqual("world_unchanged", verdict.code)
        self.assertIn("мир не менялся", verdict.reason)
        self.assertTrue(guard.allow_brain_call(T0 + 300, "b", "other").ok, "новый hash — модель вызывается")
        self.assertTrue(guard.allow_brain_call(T0 + 300, "b2", "abc").ok, "hash запоминается на духа")

    def test_failedTask_ClearsRememberedHash_SoModelIsAskedAgain(self):
        guard = make_guard()
        guard.record_decision("b", "abc")
        guard.record_outcome(T0, "b", "fp", "failed")
        guard.begin_cycle()
        self.assertTrue(guard.allow_brain_call(T0 + 300, "b", "abc").ok, "после провала мир мог остаться тем же — спрашиваем снова")

    def test_pausedBot_NoBrainCall(self):
        guard = make_guard()
        for i in range(3):
            guard.record_outcome(T0 + i, "b", f"fp{i}", "failed")
        guard.begin_cycle()
        verdict = guard.allow_brain_call(T0 + 10, "b", "h")
        self.assertFalse(verdict.ok)
        self.assertEqual("paused", verdict.code)


class CooldownAndPauseTests(unittest.TestCase):
    def test_failedPlan_SameFingerprintInCooldownBoundaryThirtyMinutes(self):
        guard = make_guard()
        fp = fingerprint("г", PLAN_A)
        guard.record_outcome(T0, "b", fp, "failed")
        verdict = guard.allow_post(T0 + 1799, "b", fp)
        self.assertFalse(verdict.ok, "тот же план после провала — кулдаун 30 мин")
        self.assertEqual("plan_cooldown", verdict.code)
        self.assertTrue(guard.allow_post(T0 + 1800, "b", fp).ok, "ровно через 30 мин кулдаун снят")

    def test_failedPlan_OtherPlanAllowed(self):
        guard = make_guard()
        guard.record_outcome(T0, "b", fingerprint("г", PLAN_A), "failed")
        self.assertTrue(guard.allow_post(T0 + 100, "b", fingerprint("г", PLAN_B)).ok, "другой план разрешён")

    def test_cancelledPlan_AlsoCooldown(self):
        guard = make_guard()
        guard.record_outcome(T0, "b", "fp", "cancelled")
        self.assertFalse(guard.allow_post(T0 + 100, "b", "fp").ok)

    def test_succeededPlan_NoCooldown(self):
        guard = make_guard()
        guard.record_outcome(T0, "b", "fp", "succeeded")
        self.assertTrue(guard.allow_post(T0 + 100, "b", "fp").ok, "успешный план повторять можно")

    def test_lostAndTimeoutOutcomes_CountAsFailures(self):
        guard = make_guard()
        guard.record_outcome(T0, "b", "fp1", "lost")
        guard.record_outcome(T0 + 1, "b", "fp2", "timeout")
        self.assertFalse(guard.allow_post(T0 + 100, "b", "fp1").ok)
        self.assertFalse(guard.allow_post(T0 + 100, "b", "fp2").ok)

    def test_threeFailuresInRow_PauseThirtyMinutesAndAlert(self):
        guard = make_guard()
        results = [guard.record_outcome(T0 + i, "b", f"fp{i}", "failed") for i in range(3)]
        self.assertEqual([False, False, True], [r.paused for r in results], "пауза включается на третьем провале")
        verdict = guard.allow_post(T0 + 100, "b", "новый-план")
        self.assertFalse(verdict.ok, "на паузе новые задачи запрещены даже с другим планом")
        self.assertEqual("paused", verdict.code)
        self.assertFalse(guard.allow_post(T0 + 2 + 1799, "b", "новый-план").ok)
        self.assertTrue(guard.allow_post(T0 + 2 + 1800, "b", "новый-план").ok, "пауза снимается ровно через 30 мин")

    def test_successInBetween_ResetsFailureStreak(self):
        guard = make_guard()
        guard.record_outcome(T0, "b", "a", "failed")
        guard.record_outcome(T0 + 1, "b", "b", "failed")
        guard.record_outcome(T0 + 2, "b", "c", "succeeded")
        result = guard.record_outcome(T0 + 3, "b", "d", "failed")
        self.assertFalse(result.paused, "серия оборвана успехом — это снова первый провал")

    def test_afterPause_StreakStartsOver(self):
        guard = make_guard()
        for i in range(3):
            guard.record_outcome(T0 + i, "b", f"f{i}", "failed")
        self.assertFalse(guard.record_outcome(T0 + 5000, "b", "x", "failed").paused, "после паузы счёт провалов с нуля")

    def test_pauseIsPerBot(self):
        guard = make_guard()
        for i in range(3):
            guard.record_outcome(T0 + i, "b1", f"f{i}", "failed")
        self.assertTrue(guard.allow_post(T0 + 100, "b2", "f").ok)

    def test_unknownOutcome_Rejected(self):
        with self.assertRaises(ValueError):
            make_guard().record_outcome(T0, "b", "fp", "приснилось")


class ProviderHealthTests(unittest.TestCase):
    def test_interval_DoublesPerFailureUpToThirtyMinutesAndResets(self):
        health = ProviderHealth(base_interval=300, max_interval=1800)
        self.assertEqual(300, health.interval())
        self.assertFalse(health.needs_probe)
        intervals = []
        for _ in range(5):
            health.failure()
            intervals.append(health.interval())
        self.assertEqual([600, 1200, 1800, 1800, 1800], intervals, "×2 до 30 мин")
        self.assertTrue(health.needs_probe, "после сбоя сначала проба, не полный промпт")
        health.success()
        self.assertEqual(300, health.interval())
        self.assertFalse(health.needs_probe)

    def test_interval_BaseAboveMax_NeverBelowBase(self):
        health = ProviderHealth(base_interval=3000, max_interval=1800)
        health.failure()
        self.assertEqual(3000, health.interval(), "интервал не опускается ниже базового")


class PersistenceTests(unittest.TestCase):
    def test_toDictFromDict_RoundTripKeepsLimits(self):
        guard = make_guard()
        for i in range(6):
            guard.record_post(T0 + i * 60, "b", "fp")
        guard.record_outcome(T0 + 400, "c", "plan", "failed")
        guard.record_decision("b", "hash")
        dumped = json.loads(json.dumps(guard.to_dict()))
        restored = Guard(from_env({}), dumped)
        self.assertFalse(restored.allow_post(T0 + 500, "b", "x").ok, "лимит часа переживает рестарт")
        self.assertFalse(restored.allow_post(T0 + 1000, "c", "plan").ok, "кулдаун плана переживает рестарт")
        restored.begin_cycle()
        self.assertFalse(restored.allow_brain_call(T0 + 500, "b", "hash").ok, "запомненный hash переживает рестарт")

    def test_toDict_PrunesExpiredEntries(self):
        guard = make_guard()
        guard.record_post(T0, "b", "fp")
        guard.record_outcome(T0, "b", "plan", "failed")
        guard.prune(T0 + 5 * HOUR)
        dumped = guard.to_dict()
        self.assertEqual([], dumped["posts"].get("b", []))
        self.assertEqual({}, dumped["cooldowns"].get("b", {}))


class GuardPropertyTests(unittest.TestCase):
    def test_property_NeverMoreThanSixPostsPerSlidingHourPerBot(self):
        for seed in range(25):
            rng = random.Random(seed)
            guard = make_guard()
            posts: dict[str, list[float]] = {}
            now = T0
            for step in range(400):
                now += rng.choice([0, 1, 5, 30, 60, 61, 300, 900, 1800])
                bot = rng.choice(["b1", "b2", "b3"])
                fp = f"fp{rng.randint(0, 5)}"
                kind = rng.random()
                if kind < 0.6:
                    if guard.allow_post(now, bot, fp).ok:
                        guard.record_post(now, bot, fp)
                        posts.setdefault(bot, []).append(now)
                elif kind < 0.9:
                    guard.record_outcome(now, bot, fp, rng.choice(["succeeded", "failed", "cancelled", "lost", "timeout"]))
                else:
                    guard.prune(now)
                    guard = Guard(from_env({}), json.loads(json.dumps(guard.to_dict())))
            for bot, times in posts.items():
                for i, start in enumerate(times):
                    in_hour = [t for t in times if start - HOUR < t <= start]
                    self.assertLessEqual(len(in_hour), 6, f"seed={seed}, дух {bot}: за скользящий час {len(in_hour)} задач (>6)")
                for earlier, later in zip(times, times[1:]):
                    self.assertGreaterEqual(later - earlier, 60, f"seed={seed}, дух {bot}: пауза между задачами меньше 60 с")


if __name__ == "__main__":
    unittest.main()
