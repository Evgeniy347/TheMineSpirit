"""Разбор ответа модели: первый сбалансированный JSON-объект -> Decision, иначе ReplyError с текстом для повтора."""

from __future__ import annotations

import json
from dataclasses import dataclass

MAX_REASON = 300
MAX_GOAL = 200
MAX_REPLY_CHARS = 65536
MAX_OBJECT_ATTEMPTS = 64
MAX_JSON_DEPTH = 40
FORMAT_HINT = ('Ответь ровно одним JSON-объектом: {"reason": "…", "goal": "…", "plan": {…}} '
               'или {"reason": "…", "skip": true}, без текста вокруг.')


class ReplyError(ValueError):
    pass


@dataclass(frozen=True)
class Decision:
    reason: str
    goal: str | None
    plan: dict | None
    skip: bool


def _reject_constant(name: str):
    raise ValueError(f"недопустимая константа {name}")


_DECODER = json.JSONDecoder(parse_constant=_reject_constant)


def _first_object(text: str):
    attempts = 0
    first_error = None
    position = text.find("{")
    while position != -1 and attempts < MAX_OBJECT_ATTEMPTS:
        attempts += 1
        try:
            value, _ = _DECODER.raw_decode(text, position)
        except (ValueError, RecursionError) as error:
            if first_error is None:
                first_error = str(error)
            position = text.find("{", position + 1)
            continue
        if isinstance(value, dict):
            return value, first_error
        position = text.find("{", position + 1)
    return None, first_error


def _depth(value) -> int:
    deepest = 0
    stack = [(value, 1)]
    while stack:
        item, level = stack.pop()
        if isinstance(item, dict):
            children = item.values()
        elif isinstance(item, list):
            children = item
        else:
            continue
        deepest = max(deepest, level)
        if deepest > MAX_JSON_DEPTH:
            return deepest
        stack.extend((child, level + 1) for child in children)
    return deepest


def parse_reply(text) -> Decision:
    if not isinstance(text, str):
        raise ReplyError(f"Ответ модели не строка ({type(text).__name__}). {FORMAT_HINT}")
    if not text.strip():
        raise ReplyError(f"Ответ пуст. {FORMAT_HINT}")
    if len(text) > MAX_REPLY_CHARS:
        raise ReplyError(f"Ответ слишком длинный ({len(text)} символов, допустимо {MAX_REPLY_CHARS}). {FORMAT_HINT}")
    found, first_error = _first_object(text)
    if found is None:
        if first_error is not None:
            raise ReplyError(f"JSON-объект не разобран, возможно ответ обрезан или в нём ошибка синтаксиса ({first_error}). "
                             f"{FORMAT_HINT}")
        raise ReplyError(f"В ответе нет JSON-объекта. {FORMAT_HINT}")
    if _depth(found) > MAX_JSON_DEPTH:
        raise ReplyError(f"JSON вложен глубже {MAX_JSON_DEPTH} уровней: упростите ответ. {FORMAT_HINT}")
    prefix = ""
    if first_error is not None:
        prefix = f"Первый JSON-объект не разобран, возможно ответ обрезан ({first_error}). "
    return _decision(found, prefix)


def _decision(found: dict, prefix: str) -> Decision:
    def fail(message: str):
        raise ReplyError(f"{prefix}{message} {FORMAT_HINT}")

    reason = found.get("reason")
    if not isinstance(reason, str) or not reason.strip():
        fail("Поле reason обязательно и должно быть непустой строкой.")
    if len(reason) > MAX_REASON:
        fail(f"Поле reason длиннее {MAX_REASON} символов: сократите.")
    has_plan = "plan" in found
    skip = found.get("skip")
    if has_plan and "skip" in found and skip is not False:
        fail("Поля plan и skip указаны одновременно: нужно одно из двух.")
    if has_plan:
        plan = found["plan"]
        if not isinstance(plan, dict):
            fail("Поле plan должно быть JSON-объектом (узлом плана).")
        goal = found.get("goal")
        if not isinstance(goal, str) or not goal.strip():
            fail("Поле goal обязательно вместе с plan и должно быть непустой строкой.")
        if len(goal) > MAX_GOAL:
            fail(f"Поле goal длиннее {MAX_GOAL} символов: сократите.")
        return Decision(reason, goal, plan, False)
    if skip is True:
        return Decision(reason, None, None, True)
    if "skip" in found:
        fail("Поле skip должно быть true (ничего не делать) или не указываться.")
    fail("Нужно поле plan или skip: true.")
