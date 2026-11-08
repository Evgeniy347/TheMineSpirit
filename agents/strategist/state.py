"""Состояние state.json: активные задачи, ожидающие отправки, счётчики стража. Запись атомарна, повреждение не прячется."""

from __future__ import annotations

import copy
import json
import os
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

SCHEMA = 1


def default_state() -> dict:
    return {"schema": SCHEMA, "cycle": 0, "active": {}, "pending": {}, "guard": {}, "alerts": {},
            "warehouse_checked": 0}


@dataclass
class LoadResult:
    state: dict
    corrupt_path: Path | None = None
    reason: str | None = None


def _shape_error(state) -> str | None:
    if not isinstance(state, dict):
        return "корень не объект"
    if state.get("schema") != SCHEMA:
        return f"незнакомая schema {state.get('schema')!r}, ожидалась {SCHEMA}"
    cycle = state.get("cycle", 0)
    if isinstance(cycle, bool) or not isinstance(cycle, int) or cycle < 0:
        return "cycle не неотрицательное целое"
    for key in ("active", "pending", "guard", "alerts"):
        if not isinstance(state.get(key, {}), dict):
            return f"поле {key} не объект"
    return None


class StateStore:
    def __init__(self, path: Path, clock):
        self.path = Path(path)
        self.clock = clock
        self._written: str | None = None

    def load(self, journal=None) -> LoadResult:
        if not self.path.exists():
            return LoadResult(default_state())
        try:
            state = json.loads(self.path.read_text(encoding="utf-8"))
            problem = _shape_error(state)
        except (ValueError, UnicodeDecodeError) as error:
            state, problem = None, f"не разобран как JSON ({error})"
        if problem is None:
            merged = default_state()
            merged.update(state)
            return LoadResult(merged)
        return self._quarantine(problem, journal)

    def _quarantine(self, problem: str, journal) -> LoadResult:
        self._written = None
        stamp = datetime.fromtimestamp(self.clock.now(), timezone.utc).strftime("%Y%m%dT%H%M%S")
        target = self.path.with_name(f"{self.path.name}.corrupt-{stamp}")
        os.replace(self.path, target)
        reason = (f"state.json повреждён ({problem}); файл сохранён как {target.name}, состояние начато заново: "
                  "активные задачи и счётчики стража потеряны, сверьте духов с реальными задачами в моде")
        if journal is not None:
            journal.log("alert", reason=reason)
        return LoadResult(default_state(), target, reason)

    def save(self, state: dict) -> None:
        payload = json.dumps(copy.deepcopy(state), ensure_ascii=False, indent=1, sort_keys=True)
        if payload == self._written:
            return
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.path.with_name(self.path.name + ".tmp")
        try:
            with open(temporary, "w", encoding="utf-8") as handle:
                handle.write(payload)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary, self.path)
            self._written = payload
        finally:
            if temporary.exists():
                temporary.unlink()
