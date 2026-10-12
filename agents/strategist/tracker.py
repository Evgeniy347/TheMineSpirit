"""Отслеживание поставленных задач: опрос GET /v1/tasks/{id}, смена state в журнал, терминальное состояние - стражу."""

from __future__ import annotations

from rest_client import ApiError, NetworkError
from runtime import Runtime

TERMINAL_STATES = ("succeeded", "failed", "cancelled")
POLL_ERRORS_BEFORE_ALERT = 6


class Tracker:
    def __init__(self, rt: Runtime):
        self.rt = rt

    def add(self, task_id: str, bot: str, goal: str, request_id: str, fingerprint: str, cycle: int, now: float) -> None:
        self.rt.state["active"][task_id] = {
            "bot": bot, "goal": goal, "request_id": request_id, "fingerprint": fingerprint, "cycle": cycle,
            "posted_at": now, "next_poll": now + self.rt.config.task_poll, "last_state": "accepted", "poll_errors": 0,
        }

    def poll(self) -> int:
        rt = self.rt
        now = rt.clock.now()
        finished = 0
        for task_id, entry in list(rt.state["active"].items()):
            if now >= entry["next_poll"]:
                finished += self._poll_one(task_id, entry, now)
        if finished or rt.state["active"]:
            rt.save()
        return finished

    def _poll_one(self, task_id: str, entry: dict, now: float) -> int:
        rt = self.rt
        entry["next_poll"] = now + rt.config.task_poll
        try:
            view = rt.client.get(f"/v1/tasks/{task_id}")
        except ApiError as error:
            if error.status == 404:
                return self._finish(task_id, entry, "lost", "задача не найдена в моде (возможно, сервер перезапускали)", now)
            return self._poll_failed(task_id, entry, str(error), now)
        except NetworkError as error:
            return self._poll_failed(task_id, entry, str(error), now)
        entry["poll_errors"] = 0
        state = view.get("state")
        if state != entry["last_state"]:
            entry["last_state"] = state
            rt.log("task_state", cycle=entry["cycle"], bot=entry["bot"], task_id=task_id, state=state,
                   progress=view.get("progress"))
        if state in TERMINAL_STATES:
            error = view.get("error")
            return self._finish(task_id, entry, state, str(error) if error else None, now)
        if now - entry["posted_at"] >= rt.config.task_timeout:
            return self._time_out(task_id, entry, now)
        return 0

    def _poll_failed(self, task_id: str, entry: dict, reason: str, now: float) -> int:
        entry["poll_errors"] += 1
        if entry["poll_errors"] == POLL_ERRORS_BEFORE_ALERT:
            self.rt.alert(f"задача {task_id} духа {entry['bot']}: опрос не удаётся {POLL_ERRORS_BEFORE_ALERT} раз подряд ({reason})",
                          code="poll_failed", bot=entry["bot"])
        if now - entry["posted_at"] >= self.rt.config.task_timeout:
            return self._time_out(task_id, entry, now)
        return 0

    def _time_out(self, task_id: str, entry: dict, now: float) -> int:
        rt = self.rt
        try:
            rt.client.delete(f"/v1/tasks/{task_id}")
        except ApiError as error:
            if error.status != 404:
                rt.alert(f"задача {task_id} духа {entry['bot']} просрочена, но DELETE не прошёл: {error}", code="delete_failed",
                         bot=entry["bot"])
                return 0
        except NetworkError as error:
            rt.alert(f"задача {task_id} духа {entry['bot']} просрочена, но DELETE не дошёл: {error}", code="delete_failed",
                     bot=entry["bot"])
            return 0
        return self._finish(task_id, entry, "timeout", f"не завершилась за {int(rt.config.task_timeout)} с, отменена", now)

    def _finish(self, task_id: str, entry: dict, outcome: str, error: str | None, now: float) -> int:
        rt = self.rt
        del rt.state["active"][task_id]
        rt.log("task_done", cycle=entry["cycle"], bot=entry["bot"], task_id=task_id, goal=entry["goal"], outcome=outcome,
               error=error, fingerprint=entry["fingerprint"], request_id=entry["request_id"],
               seconds=round(now - entry["posted_at"], 1))
        result = rt.guard.record_outcome(now, entry["bot"], entry["fingerprint"], outcome)
        if result.paused:
            rt.alert(f"дух {entry['bot']} на паузе {int(rt.config.pause_duration // 60)} мин: {result.failures_in_row} провала подряд",
                     code="bot_paused", bot=entry["bot"])
        return 1
