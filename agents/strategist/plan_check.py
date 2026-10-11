"""Локальная проверка плана до отправки: предфильтр, авторитетна проверка мода (POST /v1/plan).

Формат узлов — «Формат плана» из docs/API.md. Любой мусор на входе даёт PlanError с русским текстом, называющим путь узла.
"""

from __future__ import annotations

import json
import math
from dataclasses import dataclass
from pathlib import Path

NODE_TYPES = ("script", "sequence", "repeat", "fallback", "parallel", "condition")
NODE_FIELDS = {
    "script": ("type", "script", "params"),
    "sequence": ("type", "steps"),
    "fallback": ("type", "steps"),
    "parallel": ("type", "mode", "steps"),
    "repeat": ("type", "times", "step"),
    "condition": ("type", "check", "params", "then", "else"),
}
PARALLEL_MODES = ("all", "any")
PARAM_TYPES = ("integer", "number", "string", "boolean", "array")
MAX_ERRORS = 12


class PlanError(Exception):
    def __init__(self, errors: list[str]):
        self.errors = list(errors)
        super().__init__("; ".join(self.errors))


class CatalogError(ValueError):
    pass


@dataclass(frozen=True)
class PlanLimits:
    max_nodes: int = 32
    max_root_steps: int = 8
    max_depth: int = 16
    hard_max_nodes: int = 256
    repeat_min: int = 1
    repeat_max: int = 64


@dataclass(frozen=True)
class Policy:
    allowed_scripts: frozenset
    max_string_length: int = 200
    max_list_items: int = 32


def load_policy(path: Path) -> Policy:
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise ValueError(f"policy.json не прочитан ({path}): {error}; проверьте файл политики") from error
    allowed = data.get("allowed_scripts") if isinstance(data, dict) else None
    if not isinstance(allowed, list) or not all(isinstance(item, str) for item in allowed) or not allowed:
        raise ValueError(f"{path}: allowed_scripts должен быть непустым списком имён скриптов")
    return Policy(frozenset(allowed), int(data.get("max_string_length", 200)), int(data.get("max_list_items", 32)))


class Catalog:
    def __init__(self, scripts: dict[str, dict], conditions: dict[str, dict]):
        self.scripts = scripts
        self.conditions = conditions

    @classmethod
    def from_capabilities(cls, capabilities) -> "Catalog":
        if not isinstance(capabilities, dict) or not isinstance(capabilities.get("scripts"), list):
            raise CatalogError("Каталог capabilities без списка scripts: проверьте ответ GET /v1/capabilities")
        return cls(cls._index(capabilities["scripts"], "scripts"), cls._index(capabilities.get("conditions", []), "conditions"))

    @staticmethod
    def _index(entries, field: str) -> dict[str, dict]:
        if not isinstance(entries, list):
            raise CatalogError(f"Поле {field} каталога должно быть списком")
        index: dict[str, dict] = {}
        for entry in entries:
            if not isinstance(entry, dict) or not isinstance(entry.get("id"), str) or not entry["id"]:
                raise CatalogError(f"Запись каталога {field} без строкового id: {str(entry)[:80]}")
            params = entry.get("params", {"type": "object", "properties": {}})
            if not isinstance(params, dict) or not isinstance(params.get("properties", {}), dict) \
                    or not isinstance(params.get("required", []), list):
                raise CatalogError(f"У записи '{entry['id']}' каталога {field} схема params не объект с properties/required")
            if entry["id"] in index:
                raise CatalogError(f"Дублирующийся id '{entry['id']}' в каталоге {field}")
            index[entry["id"]] = params
        return index


class _TooManyNodes(Exception):
    pass


def check_plan(plan, catalog: Catalog, policy: Policy, limits: PlanLimits | None = None) -> None:
    limits = limits or PlanLimits()
    checker = _Checker(catalog, policy, limits)
    checker.run(plan)
    if checker.errors:
        raise PlanError(checker.errors[:MAX_ERRORS])


def _describe(value) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return repr(value) if not isinstance(value, float) or math.isfinite(value) else "не число (NaN/Infinity)"
    if isinstance(value, str):
        return repr(value if len(value) <= 40 else value[:40] + "…")
    if isinstance(value, list):
        return "массив"
    if isinstance(value, dict):
        return "объект"
    return type(value).__name__


class _Checker:
    def __init__(self, catalog: Catalog, policy: Policy, limits: PlanLimits):
        self.catalog = catalog
        self.policy = policy
        self.limits = limits
        self.errors: list[str] = []
        self.nodes = 0

    def run(self, plan) -> None:
        try:
            self._node(plan, "plan", 1)
        except _TooManyNodes:
            self.errors.append(f"В плане больше {self.limits.hard_max_nodes} узлов: жёсткий предел валидатора мода")
            return
        if self.nodes > self.limits.max_nodes:
            self.errors.append(f"В плане {self.nodes} узлов, допустимо не больше {self.limits.max_nodes}: упростите план")

    def _error(self, message: str) -> None:
        self.errors.append(message)

    def _node(self, node, path: str, depth: int) -> None:
        self.nodes += 1
        if self.nodes > self.limits.hard_max_nodes:
            raise _TooManyNodes()
        if depth > self.limits.max_depth:
            self._error(f"{path}: глубина плана больше {self.limits.max_depth}: вложите меньше уровней")
            return
        if not isinstance(node, dict):
            self._error(f"Узел плана {path} должен быть JSON-объектом, получено {_describe(node)}")
            return
        node_type = node.get("type")
        if not isinstance(node_type, str):
            self._error(f"{path}: у узла нет строкового поля 'type' (допустимы {', '.join(NODE_TYPES)})")
            return
        if node_type not in NODE_TYPES:
            self._error(f"{path}: неизвестный тип узла {node_type!r}, допустимы {', '.join(NODE_TYPES)}")
            return
        allowed_fields = NODE_FIELDS[node_type]
        for key in node:
            if key not in allowed_fields:
                self._error(f"{path}: лишнее поле {key!r} у узла {node_type} (допустимы {', '.join(allowed_fields)})")
        getattr(self, f"_{node_type}")(node, path, depth)

    def _script(self, node: dict, path: str, depth: int) -> None:
        name = node.get("script")
        if not isinstance(name, str):
            self._error(f"{path}: у узла script нет строкового поля 'script'")
            return
        schema = self.catalog.scripts.get(name)
        if schema is None:
            self._error(f"{path}: скрипта {name!r} нет в каталоге /v1/capabilities (есть: {', '.join(sorted(self.catalog.scripts))})")
            return
        if name not in self.policy.allowed_scripts:
            self._error(f"{path}: скрипт {name!r} есть в каталоге, но вне белого списка policy.json "
                        f"(разрешены: {', '.join(sorted(self.policy.allowed_scripts))})")
            return
        self._params(node.get("params"), schema, path, f"скрипта {name!r}")

    def _params(self, params, schema: dict, path: str, owner: str) -> None:
        if params is None:
            params = {}
        if not isinstance(params, dict):
            self._error(f"{path}.params: параметры {owner} должны быть объектом, получено {_describe(params)}")
            return
        properties = schema.get("properties", {})
        for key, value in params.items():
            if key not in properties:
                self._error(f"{path}.params.{key}: такого параметра нет у {owner} (допустимы: {', '.join(properties) or 'нет'})")
                continue
            self._value(value, properties[key], f"{path}.params.{key}")
        for key in schema.get("required", []):
            if key not in params:
                self._error(f"{path}.params.{key}: обязательный параметр {owner} не задан")

    def _value(self, value, schema, path: str) -> None:
        if not isinstance(schema, dict):
            self._error(f"{path}: схема параметра в каталоге не объект, проверить нельзя")
            return
        kind = schema.get("type")
        if value is None:
            self._error(f"{path}: null недопустим, уберите параметр или задайте значение")
            return
        if kind == "integer":
            self._integer(value, schema, path)
        elif kind == "number":
            self._number(value, schema, path)
        elif kind == "string":
            self._string(value, schema, path)
        elif kind == "boolean":
            if not isinstance(value, bool):
                self._error(f"{path}: ожидалось true или false, получено {_describe(value)}")
        elif kind == "array":
            self._array(value, schema, path)
        else:
            self._error(f"{path}: тип {kind!r} из каталога не поддерживается локальной проверкой")

    def _range(self, value, schema, path: str) -> None:
        minimum, maximum = schema.get("minimum"), schema.get("maximum")
        if minimum is not None and value < minimum:
            self._error(f"{path}: значение {value} меньше минимума {minimum} (схема каталога)")
        if maximum is not None and value > maximum:
            self._error(f"{path}: значение {value} больше максимума {maximum} (схема каталога)")

    def _integer(self, value, schema, path: str) -> None:
        if isinstance(value, bool) or not isinstance(value, int):
            self._error(f"{path}: ожидалось целое число, получено {_describe(value)}")
            return
        self._range(value, schema, path)

    def _number(self, value, schema, path: str) -> None:
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
            self._error(f"{path}: ожидалось конечное число, получено {_describe(value)}")
            return
        self._range(value, schema, path)

    def _string(self, value, schema, path: str) -> None:
        if not isinstance(value, str):
            self._error(f"{path}: ожидалась строка, получено {_describe(value)}")
            return
        if len(value) > self.policy.max_string_length:
            self._error(f"{path}: строка длиннее {self.policy.max_string_length} символов")
            return
        allowed = schema.get("enum")
        if allowed is not None and value not in allowed:
            self._error(f"{path}: значение {value!r} не из перечня {', '.join(map(str, allowed))}")

    def _array(self, value, schema, path: str) -> None:
        if not isinstance(value, list):
            self._error(f"{path}: ожидался массив строк, получено {_describe(value)}")
            return
        if len(value) > self.policy.max_list_items:
            self._error(f"{path}: в списке {len(value)} элементов, допустимо не больше {self.policy.max_list_items}")
            return
        for index, item in enumerate(value):
            if not isinstance(item, str):
                self._error(f"{path}[{index}]: ожидалась строка, получено {_describe(item)}")
            elif len(item) > self.policy.max_string_length:
                self._error(f"{path}[{index}]: строка длиннее {self.policy.max_string_length} символов")

    def _steps(self, node: dict, path: str, depth: int) -> None:
        steps = node.get("steps")
        if not isinstance(steps, list) or not steps:
            self._error(f"{path}.steps: должен быть непустым списком узлов, получено {_describe(steps)}")
            return
        if depth == 1 and len(steps) > self.limits.max_root_steps:
            self._error(f"{path}.steps: в корне {len(steps)} шагов, допустимо не больше {self.limits.max_root_steps}")
        for index, child in enumerate(steps):
            self._node(child, f"{path}.steps[{index}]", depth + 1)

    def _sequence(self, node: dict, path: str, depth: int) -> None:
        self._steps(node, path, depth)

    def _fallback(self, node: dict, path: str, depth: int) -> None:
        self._steps(node, path, depth)

    def _parallel(self, node: dict, path: str, depth: int) -> None:
        if node.get("mode") not in PARALLEL_MODES:
            self._error(f"{path}.mode: допустимы {', '.join(PARALLEL_MODES)}, получено {_describe(node.get('mode'))}")
        self._steps(node, path, depth)

    def _repeat(self, node: dict, path: str, depth: int) -> None:
        times = node.get("times")
        if isinstance(times, bool) or not isinstance(times, int):
            self._error(f"{path}.times: ожидалось целое число, получено {_describe(times)}")
        elif not (self.limits.repeat_min <= times <= self.limits.repeat_max):
            self._error(f"{path}.times: значение {times} вне диапазона {self.limits.repeat_min}..{self.limits.repeat_max}")
        if "step" not in node:
            self._error(f"{path}.step: у узла repeat нет шага, нечего повторять")
            return
        self._node(node["step"], f"{path}.step", depth + 1)

    def _condition(self, node: dict, path: str, depth: int) -> None:
        check = node.get("check")
        schema = self.catalog.conditions.get(check) if isinstance(check, str) else None
        if schema is None:
            known = ", ".join(sorted(self.catalog.conditions)) or "проверок нет"
            self._error(f"{path}: узел condition: проверка {_describe(check)} не найдена в каталоге условий "
                        f"(поле conditions в /v1/capabilities: {known})")
        else:
            self._params(node.get("params"), schema, path, f"проверки {check!r}")
        if "then" not in node:
            self._error(f"{path}.then: у узла condition нет ветки then")
        else:
            self._node(node["then"], f"{path}.then", depth + 1)
        if node.get("else") is not None:
            self._node(node["else"], f"{path}.else", depth + 1)
