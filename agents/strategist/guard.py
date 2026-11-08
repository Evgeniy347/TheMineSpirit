"""Страж спама и зацикливания: лимиты задач и вызовов модели, кулдауны планов, паузы духов, здоровье провайдера.

Время приходит аргументом now (секунды); состояние - обычный словарь, который переживает рестарт через state.json.
"""

from __future__ import annotations

import hashlib
import json
import uuid
from dataclasses import dataclass

from config import Config

HOUR = 3600.0
FAILED_OUTCOMES = ("failed", "lost", "timeout")
OUTCOMES = ("succeeded", "cancelled") + FAILED_OUTCOMES
REQUEST_NAMESPACE = uuid.UUID("5b0f6d52-3c1e-4a57-9d4e-7a6f1c2b9e10")


@dataclass(frozen=True)
class Verdict:
    ok: bool
    reason: str = ""
    code: str = ""


@dataclass(frozen=True)
class OutcomeResult:
    paused: bool
    failures_in_row: int


_DROP = object()


def _without_numbers(value):
    """Структура плана без чисел: count, radius, координаты и прочие величины не создают новый план."""
    if isinstance(value, dict):
        result = {}
        for key, item in value.items():
            cleaned = _without_numbers(item)
            if cleaned is not _DROP:
                result[key] = cleaned
        return result
    if isinstance(value, list):
        return [cleaned for item in value if (cleaned := _without_numbers(item)) is not _DROP]
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return _DROP
    return value


def fingerprint(plan: dict) -> str:
    """Точный хэш нормализованного плана: скрипт и ключевые (нечисловые) параметры без чисел.

    План, отличающийся от проваленного только count или радиусом, обязан попасть в тот же кулдаун:
    иначе модель обходит запрет повтора, меняя одно число.
    """
    canonical = json.dumps(_without_numbers(plan), sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def request_id(bot: str, cycle: int, plan_fingerprint: str) -> str:
    return str(uuid.uuid5(REQUEST_NAMESPACE, f"{bot}|{cycle}|{plan_fingerprint}"))


def warehouse_request_id(level: int, site: str) -> str:
    return str(uuid.uuid5(REQUEST_NAMESPACE, f"warehouse|{level}|{site}"))


class Guard:
    def __init__(self, config: Config, data: dict | None = None):
        self.config = config
        data = data or {}
        self.posts: dict[str, list[float]] = {k: list(v) for k, v in data.get("posts", {}).items()}
        self.brain_calls: list[float] = list(data.get("brain_calls", []))
        self.cooldowns: dict[str, dict[str, float]] = {k: dict(v) for k, v in data.get("cooldowns", {}).items()}
        self.failures: dict[str, int] = dict(data.get("failures", {}))
        self.paused_until: dict[str, float] = dict(data.get("paused_until", {}))
        self.last_hash: dict[str, str] = dict(data.get("last_hash", {}))
        self.dry_plans: dict[str, str] = dict(data.get("dry_plans", {}))
        self._cycle_calls: dict[str, int] = {}

    def to_dict(self) -> dict:
        return {"posts": self.posts, "brain_calls": self.brain_calls, "cooldowns": self.cooldowns,
                "failures": self.failures, "paused_until": self.paused_until, "last_hash": self.last_hash,
                "dry_plans": self.dry_plans}

    def begin_cycle(self) -> None:
        self._cycle_calls = {}

    def prune(self, now: float) -> None:
        for bot in list(self.posts):
            self.posts[bot] = [t for t in self.posts[bot] if t > now - HOUR]
            if not self.posts[bot]:
                del self.posts[bot]
        self.brain_calls = [t for t in self.brain_calls if t > now - HOUR]
        for bot in list(self.cooldowns):
            self.cooldowns[bot] = {fp: until for fp, until in self.cooldowns[bot].items() if until > now}
            if not self.cooldowns[bot]:
                del self.cooldowns[bot]
        for bot in list(self.paused_until):
            if self.paused_until[bot] <= now:
                del self.paused_until[bot]

    def _paused(self, now: float, bot: str) -> Verdict | None:
        until = self.paused_until.get(bot)
        if until is not None and now < until:
            return Verdict(False, f"дух {bot} на паузе ещё {int(until - now)} с после серии провалов", "paused")
        return None

    def allow_brain_call(self, now: float, bot: str, obs_hash: str | None = None, retry: bool = False) -> Verdict:
        paused = self._paused(now, bot)
        if paused:
            return paused
        used = self._cycle_calls.get(bot, 0)
        if used >= (2 if retry else 1):
            return Verdict(False, f"для духа {bot} в этом цикле модель уже вызывалась {used} раз(а)", "cycle_calls")
        recent = [t for t in self.brain_calls if t > now - HOUR]
        if len(recent) >= self.config.brain_calls_per_hour:
            return Verdict(False, f"за час уже {len(recent)} вызовов модели (предел {self.config.brain_calls_per_hour})",
                           "brain_hourly")
        if not retry and obs_hash is not None and self.last_hash.get(bot) == obs_hash:
            return Verdict(False, "мир не менялся с прошлого решения (hash наблюдения тот же)", "world_unchanged")
        return Verdict(True)

    def record_brain_call(self, now: float, bot: str) -> None:
        self.brain_calls.append(now)
        self._cycle_calls[bot] = self._cycle_calls.get(bot, 0) + 1
        self.brain_calls = [t for t in self.brain_calls if t > now - HOUR]

    def record_decision(self, bot: str, obs_hash: str | None) -> None:
        if obs_hash is None:
            self.last_hash.pop(bot, None)
        else:
            self.last_hash[bot] = obs_hash

    def record_dry_decision(self, bot: str, plan_fingerprint: str) -> bool:
        """Запомнить dry-решение; True — такой же план уже записан, это повтор, а не новое решение."""
        duplicate = self.dry_plans.get(bot) == plan_fingerprint
        self.dry_plans[bot] = plan_fingerprint
        return duplicate

    def forget_dry_decision(self, bot: str) -> None:
        self.dry_plans.pop(bot, None)

    def allow_new_task(self, now: float, bot: str) -> Verdict:
        paused = self._paused(now, bot)
        if paused:
            return paused
        recent = [t for t in self.posts.get(bot, []) if t > now - HOUR]
        if len(recent) >= self.config.tasks_per_hour:
            return Verdict(False, f"за час духу {bot} уже поставлено {len(recent)} задач (предел {self.config.tasks_per_hour})",
                           "tasks_hourly")
        if recent and now - max(recent) < self.config.task_gap:
            return Verdict(False, f"с прошлой задачи духа {bot} прошло {int(now - max(recent))} с, нужно не меньше "
                           f"{int(self.config.task_gap)}", "task_gap")
        return Verdict(True)

    def allow_post(self, now: float, bot: str, plan_fingerprint: str) -> Verdict:
        allowed = self.allow_new_task(now, bot)
        if not allowed.ok:
            return allowed
        until = self.cooldowns.get(bot, {}).get(plan_fingerprint)
        if until is not None and now < until:
            return Verdict(False, f"этот же план провалился недавно, повтор через {int(until - now)} с", "plan_cooldown")
        return Verdict(True)

    def record_post(self, now: float, bot: str, plan_fingerprint: str) -> None:
        posts = [t for t in self.posts.get(bot, []) if t > now - HOUR]
        posts.append(now)
        self.posts[bot] = posts
        self.forget_dry_decision(bot)

    def record_outcome(self, now: float, bot: str, plan_fingerprint: str, outcome: str) -> OutcomeResult:
        if outcome not in OUTCOMES:
            raise ValueError(f"Неизвестный исход задачи {outcome!r}: допустимы {', '.join(OUTCOMES)}")
        if outcome == "succeeded":
            self.failures[bot] = 0
            return OutcomeResult(False, 0)
        if outcome == "cancelled":
            self.last_hash.pop(bot, None)
            return OutcomeResult(False, self.failures.get(bot, 0))
        return self.record_failure(now, bot, plan_fingerprint)

    def record_failure(self, now: float, bot: str, plan_fingerprint: str) -> OutcomeResult:
        self.cooldowns.setdefault(bot, {})[plan_fingerprint] = now + self.config.plan_cooldown
        self.last_hash.pop(bot, None)
        streak = self.failures.get(bot, 0) + 1
        if streak >= self.config.failures_to_pause:
            self.failures[bot] = 0
            self.paused_until[bot] = now + self.config.pause_duration
            return OutcomeResult(True, streak)
        self.failures[bot] = streak
        return OutcomeResult(False, streak)


class ProviderHealth:
    def __init__(self, base_interval: float, max_interval: float = 1800.0):
        self.base_interval = base_interval
        self.max_interval = max_interval
        self.failures = 0

    @property
    def needs_probe(self) -> bool:
        return self.failures > 0

    def failure(self) -> None:
        self.failures += 1

    def success(self) -> None:
        self.failures = 0

    def interval(self) -> float:
        if self.failures == 0:
            return self.base_interval
        return max(self.base_interval, min(self.base_interval * 2 ** self.failures, self.max_interval))
