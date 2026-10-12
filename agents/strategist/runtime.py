"""Общий контекст исполнения: конфиг, клиент, журнал, страж, состояние, часы. Один объект на процесс."""

from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field

from journal import is_recent_decision

RECENT_CACHE = 500


@dataclass
class Runtime:
    config: object
    client: object
    journal: object
    guard: object
    store: object
    state: dict
    clock: object
    out: object = print
    strict: bool = False
    recent_events: deque = field(default_factory=lambda: deque(maxlen=RECENT_CACHE))

    def load_recent(self) -> int:
        result = self.journal.read()
        for event in result.events:
            if is_recent_decision(event):
                self.recent_events.append(event)
        if result.bad:
            self.log("alert", reason=f"в журнале пропущено {len(result.bad)} строк (битые или незнакомой schema): "
                                     f"{result.bad[0][0]}:{result.bad[0][1]} {result.bad[0][2]}", code="journal_bad_lines")
        return len(result.events)

    def log(self, event: str, cycle: int | None = None, bot: str | None = None, **fields) -> dict:
        record = self.journal.log(event, cycle=cycle, bot=bot, **fields)
        if is_recent_decision(record):
            self.recent_events.append(record)
        return record

    def recent_for(self, bot: str, limit: int = 5) -> list[dict]:
        return self.journal.recent_decisions(bot, limit, events=list(self.recent_events))

    def say(self, text: str) -> None:
        if self.out is not None:
            self.out(text)

    def alert(self, reason: str, code: str = "", cycle: int | None = None, bot: str | None = None) -> None:
        self.log("alert", cycle=cycle, bot=bot, reason=reason, code=code or None)
        self.say(f"ВНИМАНИЕ: {reason}")

    def save(self) -> None:
        self.state["guard"] = self.guard.to_dict()
        self.store.save(self.state)
