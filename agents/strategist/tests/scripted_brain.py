"""Фейковый «мозг» для сценариев: отвечает по сценарию, запоминает промпты. Реальных вызовов opencode в тестах нет."""

from __future__ import annotations

import json

from brain import BrainError, BrainReply, BrainUnavailable, SandboxBreach


class ScriptedBrain:
    def __init__(self, model: str, script: list | None = None, default: dict | None = None, probe: list | None = None,
                 probe_default: dict | None = None):
        self.model = model
        self.script = list(script or [])
        self.default = default
        self.probe_script = list(probe or [])
        self.probe_default = probe_default if probe_default is not None else {"text": "OK"}
        self.calls: list[tuple[str, str]] = []
        self.probes = 0

    def _next(self, queue: list, default: dict | None, what: str) -> dict:
        if queue:
            return queue.pop(0)
        if default is not None:
            return default
        raise AssertionError(f"Модель {self.model} вызвана ({what}) больше раз, чем предусмотрено сценарием: "
                             "добавьте ответ в brain.script или default, либо найдите лишний вызов")

    def _result(self, entry: dict) -> BrainReply:
        if "unavailable" in entry:
            raise BrainUnavailable(entry["unavailable"], entry.get("text", f"{self.model}: недоступна"))
        if "breach" in entry:
            raise SandboxBreach(f"Нарушение песочницы: инструмент {entry['breach']} выполнился без отказа")
        if "error" in entry:
            raise BrainError(entry["error"])
        if "json" in entry:
            text = json.dumps(entry["json"], ensure_ascii=False)
        elif "fenced" in entry:
            text = "```json\n" + json.dumps(entry["fenced"], ensure_ascii=False, indent=2) + "\n```"
        else:
            text = entry["text"]
        return BrainReply(text, self.model, 0.5, entry.get("tool_attempts", 0))

    def complete(self, system: str, user: str) -> BrainReply:
        self.calls.append((system, user))
        return self._result(self._next(self.script, self.default, "complete"))

    def probe(self) -> BrainReply:
        self.probes += 1
        return self._result(self._next(self.probe_script, self.probe_default, "probe"))

    def request_stop(self) -> None:
        """Подпроцессов нет: останавливать нечего, но интерфейс мозга обязан быть полным."""

    def keep_reply_failure(self, reply) -> None:
        """Сырой вывод подпроцесса сохраняет только OpencodeBrain; сценарному мозгу сохранять нечего."""
