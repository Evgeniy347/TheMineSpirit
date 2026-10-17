"""Конфигурация стратега: переменные окружения -> объект Config.

Неверное значение - ConfigError с именем переменной и допустимым диапазоном, молчаливых умолчаний нет.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Mapping

DEFAULT_API = "http://127.0.0.1:8765"
DEFAULT_HOME = "/opt/minecraft/strategist"
DEFAULT_MODEL = "opencode/longcat-2.5-preview-free"
DEFAULT_MODEL_FALLBACK = "opencode/nemotron-3-ultra-free"
MODES = ("dry", "live")
BRAINS = ("opencode", "command")
APP_DIR = Path(__file__).resolve().parent
REPO_ROOT = APP_DIR.parent.parent


class ConfigError(ValueError):
    pass


@dataclass(frozen=True)
class Config:
    api: str = DEFAULT_API
    home: Path = Path(DEFAULT_HOME)
    interval: float = 300
    mode: str = "dry"
    brain: str = "opencode"
    model: str = DEFAULT_MODEL
    model_fallback: str = DEFAULT_MODEL_FALLBACK
    brain_command: str = ""
    brain_timeout: float = 240
    probe_timeout: float = 60
    opencode_bin: str = "opencode"
    tasks_per_hour: int = 10
    task_gap: float = 60
    brain_calls_per_hour: int = 30
    plan_cooldown: float = 1800
    failures_to_pause: int = 3
    pause_duration: float = 300
    task_poll: float = 10
    pending_ttl: float = 900
    task_timeout: float = 1800
    rest_per_second: int = 5
    rest_timeout: float = 10
    observe_radius: int = 24
    max_nodes: int = 32
    max_root_steps: int = 8
    journal_max_bytes: int = 10 * 1024 * 1024
    journal_files: int = 5
    max_interval_backoff: float = 1800
    confirm_live: bool = False
    repo_root: Path = field(default=REPO_ROOT)

    @property
    def state_path(self) -> Path:
        return self.home / "state.json"

    @property
    def journal_path(self) -> Path:
        return self.home / "journal.jsonl"

    @property
    def pause_path(self) -> Path:
        return self.home / "PAUSE"

    @property
    def sandbox_dir(self) -> Path:
        return self.home / "sandbox"

    @property
    def opencode_data(self) -> Path:
        return self.home / "opencode-data"

    @property
    def policy_path(self) -> Path:
        return APP_DIR / "policy.json"


def _number(env: Mapping[str, str], name: str, default, low, high, cast: Callable):
    raw = env.get(name)
    if raw is None or raw == "":
        return default
    try:
        value = cast(raw)
    except ValueError:
        raise ConfigError(f"{name}={raw!r}: ожидается число от {low} до {high}") from None
    if not (low <= value <= high):
        raise ConfigError(f"{name}={raw}: допустимо от {low} до {high}")
    return value


def _choice(env: Mapping[str, str], name: str, default: str, allowed: tuple) -> str:
    raw = env.get(name)
    if raw is None or raw == "":
        return default
    if raw not in allowed:
        raise ConfigError(f"{name}={raw!r}: допустимо одно из {', '.join(allowed)}")
    return raw


def _text(env: Mapping[str, str], name: str, default: str, pattern_hint: str = "непустая строка") -> str:
    raw = env.get(name)
    if raw is None:
        return default
    if not raw.strip():
        raise ConfigError(f"{name} задана пустой: ожидается {pattern_hint}")
    return raw.strip()


def _api_url(env: Mapping[str, str]) -> str:
    value = _text(env, "SPIRIT_API", DEFAULT_API, "адрес вида http://хост:порт")
    if not value.startswith(("http://", "https://")):
        raise ConfigError(f"SPIRIT_API={value!r}: адрес должен начинаться с http:// или https://")
    return value.rstrip("/")


def from_env(env: Mapping[str, str] | None = None) -> Config:
    env = os.environ if env is None else env
    brain = _choice(env, "STRATEGIST_BRAIN", "opencode", BRAINS)
    command = env.get("STRATEGIST_BRAIN_COMMAND", "").strip()
    if brain == "command" and not command:
        raise ConfigError("STRATEGIST_BRAIN=command требует STRATEGIST_BRAIN_COMMAND: команду, читающую JSON со stdin")
    confirm = env.get("STRATEGIST_CONFIRM_LIVE", "")
    if confirm not in ("", "0", "1"):
        raise ConfigError(f"STRATEGIST_CONFIRM_LIVE={confirm!r}: допустимо 0 или 1")
    return Config(
        api=_api_url(env),
        home=Path(_text(env, "STRATEGIST_HOME", DEFAULT_HOME, "путь к рабочему каталогу")),
        interval=_number(env, "STRATEGIST_INTERVAL", 300, 60, 3600, float),
        mode=_choice(env, "STRATEGIST_MODE", "dry", MODES),
        brain=brain,
        model=_text(env, "STRATEGIST_MODEL", DEFAULT_MODEL, "имя модели вида провайдер/модель"),
        model_fallback=_text(env, "STRATEGIST_MODEL_FALLBACK", DEFAULT_MODEL_FALLBACK, "имя модели вида провайдер/модель"),
        brain_command=command,
        brain_timeout=_number(env, "STRATEGIST_BRAIN_TIMEOUT", 240, 5, 1800, float),
        probe_timeout=_number(env, "STRATEGIST_PROBE_TIMEOUT", 60, 3, 600, float),
        opencode_bin=_text(env, "STRATEGIST_OPENCODE_BIN", str(Path.home() / ".opencode" / "bin" / "opencode")),
        tasks_per_hour=_number(env, "STRATEGIST_TASKS_PER_HOUR", 10, 1, 60, int),
        task_gap=_number(env, "STRATEGIST_TASK_GAP", 60, 0, 3600, float),
        brain_calls_per_hour=_number(env, "STRATEGIST_BRAIN_CALLS_PER_HOUR", 30, 1, 600, int),
        plan_cooldown=_number(env, "STRATEGIST_PLAN_COOLDOWN", 1800, 0, 86400, float),
        failures_to_pause=_number(env, "STRATEGIST_FAILURES_TO_PAUSE", 3, 1, 20, int),
        pause_duration=_number(env, "STRATEGIST_PAUSE_DURATION", 300, 0, 86400, float),
        task_poll=_number(env, "STRATEGIST_TASK_POLL", 10, 1, 300, float),
        pending_ttl=_number(env, "STRATEGIST_PENDING_TTL", 900, 60, 86400, float),
        task_timeout=_number(env, "STRATEGIST_TASK_TIMEOUT", 1800, 30, 86400, float),
        rest_per_second=_number(env, "STRATEGIST_REST_PER_SECOND", 5, 1, 20, int),
        rest_timeout=_number(env, "STRATEGIST_REST_TIMEOUT", 10, 1, 120, float),
        observe_radius=_number(env, "STRATEGIST_OBSERVE_RADIUS", 24, 1, 32, int),
        max_nodes=_number(env, "STRATEGIST_MAX_NODES", 32, 1, 256, int),
        max_root_steps=_number(env, "STRATEGIST_MAX_ROOT_STEPS", 8, 1, 64, int),
        journal_max_bytes=_number(env, "STRATEGIST_JOURNAL_MAX_BYTES", 10 * 1024 * 1024, 1024, 1 << 30, int),
        journal_files=_number(env, "STRATEGIST_JOURNAL_FILES", 5, 1, 50, int),
        max_interval_backoff=_number(env, "STRATEGIST_MAX_BACKOFF", 1800, 60, 86400, float),
        confirm_live=confirm == "1",
    )
