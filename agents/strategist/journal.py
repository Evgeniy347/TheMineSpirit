"""Журнал решений journal.jsonl: по строке на событие, усечение ответа модели, ротация по размеру."""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

SCHEMA = 1
EVENTS = ("cycle_start", "bot_skipped", "observed", "decision", "rejected", "posted", "task_state", "task_done",
          "brain_failure", "alert")
MAX_REPLY_BYTES = 4096
TRUNCATION_MARK = "…[усечено]"
RECENT_EVENTS = ("task_done", "rejected")


@dataclass
class ReadResult:
    events: list[dict] = field(default_factory=list)
    bad: list[tuple[str, int, str]] = field(default_factory=list)


def truncate_utf8(text: str, limit: int) -> tuple[str, bool]:
    raw = text.encode("utf-8")
    if len(raw) <= limit:
        return text, False
    return raw[:limit].decode("utf-8", errors="ignore") + TRUNCATION_MARK, True


class Journal:
    def __init__(self, path: Path, clock, max_bytes: int, files: int):
        self.path = Path(path)
        self.clock = clock
        self.max_bytes = max_bytes
        self.files = files

    def log(self, event: str, cycle: int | None = None, bot: str | None = None, **fields) -> dict:
        if event not in EVENTS:
            raise ValueError(f"Неизвестное событие журнала {event!r}: допустимы {', '.join(EVENTS)}; "
                             "добавьте имя в journal.EVENTS, если событие новое")
        record = {"schema": SCHEMA, "ts": datetime.fromtimestamp(self.clock.now(), timezone.utc).isoformat(timespec="seconds"),
                  "event": event, "cycle": cycle, "bot": bot}
        record.update(fields)
        if isinstance(record.get("reply"), str):
            record["reply"], truncated = truncate_utf8(record["reply"], MAX_REPLY_BYTES)
            if truncated:
                record["reply_truncated"] = True
        record = {key: value for key, value in record.items() if value is not None}
        line = json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n"
        self._append(line.encode("utf-8"))
        return record

    def _append(self, data: bytes) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        if self.path.exists() and self.path.stat().st_size > 0 and self.path.stat().st_size + len(data) > self.max_bytes:
            self._rotate()
        with open(self.path, "ab") as handle:
            handle.write(data)

    def _archive(self, index: int) -> Path:
        return self.path.with_name(f"{self.path.name}.{index}")

    def _rotate(self) -> None:
        archives = self.files - 1
        if archives <= 0:
            self.path.unlink()
            return
        oldest = self._archive(archives)
        if oldest.exists():
            oldest.unlink()
        for index in range(archives - 1, 0, -1):
            if self._archive(index).exists():
                os.replace(self._archive(index), self._archive(index + 1))
        os.replace(self.path, self._archive(1))

    def _files_oldest_first(self) -> list[Path]:
        archives = [self._archive(i) for i in range(self.files - 1, 0, -1)]
        return [p for p in archives + [self.path] if p.exists()]

    def read(self) -> ReadResult:
        result = ReadResult()
        for path in self._files_oldest_first():
            with open(path, encoding="utf-8", errors="replace") as handle:
                for number, line in enumerate(handle, 1):
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        record = json.loads(line)
                    except ValueError:
                        result.bad.append((path.name, number, "строка не JSON"))
                        continue
                    if not isinstance(record, dict) or record.get("schema") != SCHEMA:
                        schema = record.get("schema") if isinstance(record, dict) else None
                        result.bad.append((path.name, number, f"незнакомая schema {schema!r}, ожидалась {SCHEMA}"))
                        continue
                    result.events.append(record)
        return result

    def recent_decisions(self, bot: str, limit: int, events: list[dict] | None = None) -> list[dict]:
        source = self.read().events if events is None else events
        relevant = [e for e in source if e.get("bot") == bot and (
            e.get("event") in RECENT_EVENTS or (e.get("event") == "decision" and e.get("skip") is True))]
        return relevant[-limit:]
