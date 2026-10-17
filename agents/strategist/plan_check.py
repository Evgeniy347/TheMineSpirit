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

PICKAXE_LEVELS = {
    "wooden_pickaxe": 1, "golden_pickaxe": 1,
    "stone_pickaxe": 2,
    "iron_pickaxe": 3,
    "diamond_pickaxe": 4,
    "netherite_pickaxe": 5,
}
PICKAXE_REQUIREMENT = {
    1: "кирка (любая, даже деревянная)",
    2: "каменная кирка или лучше",
    3: "железная кирка или лучше",
    4: "алмазная кирка или лучше",
}
ORE_PICKAXE_LEVEL = {
    "coal": 1, "quartz": 1,
    "copper": 2, "iron": 2, "lapis": 2,
    "gold": 3, "redstone": 3, "diamond": 3, "emerald": 3,
    "netherite": 4,
}
BLOCK_PICKAXE_LEVEL = {
    name: 1 for name in (
        "stone", "deepslate", "granite", "diorite", "andesite", "tuff", "calcite", "dripstone_block", "basalt",
        "smooth_basalt", "blackstone", "netherrack", "end_stone", "sandstone", "red_sandstone", "crimson_nylium",
        "warped_nylium", "glowstone", "magma_block", "amethyst_block", "prismarine", "prismarine_bricks",
        "dark_prismarine", "sea_lantern", "coal_ore", "deepslate_coal_ore", "nether_quartz_ore")
}
BLOCK_PICKAXE_LEVEL.update({
    name: 2 for name in ("iron_ore", "deepslate_iron_ore", "copper_ore", "deepslate_copper_ore", "lapis_ore",
                         "deepslate_lapis_ore", "raw_iron_block", "raw_copper_block")
})
BLOCK_PICKAXE_LEVEL.update({
    name: 3 for name in ("gold_ore", "deepslate_gold_ore", "nether_gold_ore", "redstone_ore", "deepslate_redstone_ore",
                         "diamond_ore", "deepslate_diamond_ore", "emerald_ore", "deepslate_emerald_ore", "raw_gold_block")
})
BLOCK_PICKAXE_LEVEL.update({name: 4 for name in ("obsidian", "ancient_debris")})

ITEM_SCRIPTS = ("acquire", "craft", "equip", "withdraw")
ITEM_PATTERN_SCRIPTS = frozenset({"equip"})
ITEM_ID_SYMBOLS = frozenset("abcdefghijklmnopqrstuvwxyz0123456789_:./-")


def item_id_problem(value: str, allow_pattern: bool) -> str | None:
    """Почему строка не похожа на id предмета (None — похожа). Шаблон '*' разрешён только equip, как IdFilter мода."""
    if not value:
        return "id пуст"
    if value.count(":") > 1:
        return "двоеточий больше одного"
    namespace, separator, path = value.partition(":")
    if separator and not namespace:
        return "пустое пространство имён до ':'"
    if separator and not path:
        return "пустой id после ':'"
    allowed = ITEM_ID_SYMBOLS | ({"*"} if allow_pattern else frozenset())
    bad = sorted({symbol for symbol in value if symbol not in allowed})
    if bad:
        return "недопустимые символы " + ", ".join(repr(symbol) for symbol in bad)
    return None


def required_pickaxe(script: str, params: dict) -> int | None:
    if script == "mine_vein":
        ore = params.get("ore", "any")
        if ore == "any":
            return 1
        return ORE_PICKAXE_LEVEL.get(ore) if isinstance(ore, str) else None
    if script == "gather":
        block = params.get("block")
        return BLOCK_PICKAXE_LEVEL.get(normalize_block(block)) if isinstance(block, str) else None
    return None


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
    max_goto_distance: int = 256
    observed_block_params: tuple = (("gather", "block"),)
    natural_blocks: frozenset = frozenset()


@dataclass(frozen=True)
class PlanContext:
    position: tuple | None
    observed_blocks: frozenset | None
    inventory: frozenset | None = None


def normalize_block(name: str) -> str:
    return name[len("minecraft:"):] if name.startswith("minecraft:") else name


def context_from_observation(observation: dict) -> PlanContext:
    pos = observation.get("pos")
    position = tuple(pos) if isinstance(pos, list) and len(pos) == 3 and all(
        isinstance(v, (int, float)) and not isinstance(v, bool) for v in pos) else None
    blocks = observation.get("blocks")
    observed = frozenset(normalize_block(entry["item"]) for entry in blocks if isinstance(entry, dict)
                         and isinstance(entry.get("item"), str)) if isinstance(blocks, list) else frozenset()
    inventory = _inventory_from(observation)
    return PlanContext(position, observed, inventory)


def _inventory_from(observation: dict) -> frozenset | None:
    items = observation.get("inventory")
    known = isinstance(items, list)
    inventory = frozenset(normalize_block(entry["item"]) for entry in items if isinstance(entry, dict)
                          and isinstance(entry.get("item"), str)) if known else frozenset()
    held = observation.get("held")
    held_item = held.get("item") if isinstance(held, dict) and isinstance(held.get("item"), str) else None
    if held_item is not None:
        inventory = inventory | {normalize_block(held_item)}
        known = True
    return inventory if known else None


def load_policy(path: Path) -> Policy:
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise ValueError(f"policy.json не прочитан ({path}): {error}; проверьте файл политики") from error
    allowed = data.get("allowed_scripts") if isinstance(data, dict) else None
    if not isinstance(allowed, list) or not all(isinstance(item, str) for item in allowed) or not allowed:
        raise ValueError(f"{path}: allowed_scripts должен быть непустым списком имён скриптов")
    block_params = data.get("observed_block_params", {"gather": "block"})
    if not isinstance(block_params, dict) or not all(isinstance(k, str) and isinstance(v, str) for k, v in block_params.items()):
        raise ValueError(f"{path}: observed_block_params должен быть объектом вида {{\"скрипт\": \"параметр\"}}")
    natural = data.get("natural_blocks")
    if not isinstance(natural, dict) or not natural or not all(
            isinstance(group, str) and group and isinstance(items, list) and items
            and all(isinstance(item, str) and item and ":" not in item for item in items)
            for group, items in natural.items()):
        raise ValueError(f"{path}: natural_blocks должен быть объектом вида {{\"группа\": [\"блок\", …]}} с непустыми "
                         "списками имён блоков без префикса minecraft: — по нему gather.block отличает природный блок "
                         "от постройки игрока")
    natural_blocks = frozenset(item for items in natural.values() for item in items)
    return Policy(frozenset(allowed), int(data.get("max_string_length", 200)), int(data.get("max_list_items", 32)),
                  int(data.get("max_goto_distance", 256)), tuple(sorted(block_params.items())), natural_blocks)


class Catalog:
    def __init__(self, scripts: dict[str, dict], conditions: dict[str, dict], raw: dict | None = None):
        self.scripts = scripts
        self.conditions = conditions
        self.raw = raw if raw is not None else {}

    @classmethod
    def from_capabilities(cls, capabilities) -> "Catalog":
        if not isinstance(capabilities, dict) or not isinstance(capabilities.get("scripts"), list):
            raise CatalogError("Каталог capabilities без списка scripts: проверьте ответ GET /v1/capabilities")
        return cls(cls._index(capabilities["scripts"], "scripts"), cls._index(capabilities.get("conditions", []), "conditions"),
                   capabilities)

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


def check_plan(plan, catalog: Catalog, policy: Policy, limits: PlanLimits | None = None,
               context: PlanContext | None = None) -> list[str]:
    limits = limits or PlanLimits()
    checker = _Checker(catalog, policy, limits, context)
    checker.run(plan)
    if checker.errors:
        raise PlanError(checker.errors[:MAX_ERRORS])
    return checker.warnings[:MAX_ERRORS]


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
    def __init__(self, catalog: Catalog, policy: Policy, limits: PlanLimits, context: PlanContext | None = None):
        self.context = context
        self.catalog = catalog
        self.policy = policy
        self.limits = limits
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.nodes = 0

    def run(self, plan) -> None:
        inventory = self.context.inventory if self.context is not None else None
        try:
            self._node(plan, "plan", 1, inventory)
        except _TooManyNodes:
            self.errors.append(f"В плане больше {self.limits.hard_max_nodes} узлов: жёсткий предел валидатора мода")
            return
        if self.nodes > self.limits.max_nodes:
            self.errors.append(f"В плане {self.nodes} узлов, допустимо не больше {self.limits.max_nodes}: упростите план")

    def _error(self, message: str) -> None:
        self.errors.append(message)

    def _node(self, node, path: str, depth: int, inventory) -> frozenset | None:
        """Проверить узел, вернув инвентарь духа, доступный после его шагов (кирку накапливают sequence/fallback)."""
        self.nodes += 1
        if self.nodes > self.limits.hard_max_nodes:
            raise _TooManyNodes()
        if depth > self.limits.max_depth:
            self._error(f"{path}: глубина плана больше {self.limits.max_depth}: вложите меньше уровней")
            return inventory
        if not isinstance(node, dict):
            self._error(f"Узел плана {path} должен быть JSON-объектом, получено {_describe(node)}")
            return inventory
        node_type = node.get("type")
        if not isinstance(node_type, str):
            self._error(f"{path}: у узла нет строкового поля 'type' (допустимы {', '.join(NODE_TYPES)})")
            return inventory
        if node_type not in NODE_TYPES:
            self._error(f"{path}: неизвестный тип узла {node_type!r}, допустимы {', '.join(NODE_TYPES)}")
            return inventory
        allowed_fields = NODE_FIELDS[node_type]
        for key in node:
            if key not in allowed_fields:
                self._error(f"{path}: лишнее поле {key!r} у узла {node_type} (допустимы {', '.join(allowed_fields)})")
        return getattr(self, f"_{node_type}")(node, path, depth, inventory)

    def _script(self, node: dict, path: str, depth: int, inventory) -> frozenset | None:
        name = node.get("script")
        if not isinstance(name, str):
            self._error(f"{path}: у узла script нет строкового поля 'script'")
            return inventory
        schema = self.catalog.scripts.get(name)
        if schema is None:
            self._error(f"{path}: скрипта {name!r} нет в каталоге /v1/capabilities (есть: {', '.join(sorted(self.catalog.scripts))})")
            return inventory
        if name not in self.policy.allowed_scripts:
            self._error(f"{path}: скрипт {name!r} есть в каталоге, но вне белого списка policy.json "
                        f"(разрешены: {', '.join(sorted(self.policy.allowed_scripts))})")
            return inventory
        before = len(self.errors)
        self._params(node.get("params"), schema, path, f"скрипта {name!r}")
        if len(self.errors) == before and isinstance(node.get("params"), dict):
            self._item_rule(name, node["params"], path)
            if self.context is not None:
                self._world_rules(name, node["params"], path, inventory)
            return self._after_item_script(name, node["params"], inventory)
        return inventory

    def _after_item_script(self, name: str, params: dict, inventory) -> frozenset | None:
        """Инвентарь после шага: acquire/craft/withdraw кладут предмет в инвентарь, equip — в руку."""
        if name not in ITEM_SCRIPTS or inventory is None:
            return inventory
        item = params.get("item")
        if not isinstance(item, str) or "*" in item:
            return inventory
        return inventory | {normalize_block(item)}

    def _item_rule(self, name: str, params: dict, path: str) -> None:
        if name not in ITEM_SCRIPTS:
            return
        value = params.get("item")
        if not isinstance(value, str):
            return
        problem = item_id_problem(value, name in ITEM_PATTERN_SCRIPTS)
        if problem is None:
            return
        hint = " (для equip допустим шаблон вида '*_pickaxe')" if name in ITEM_PATTERN_SCRIPTS else ""
        self._error(f"{path}.params.item: {value!r} — не id предмета: {problem}; пишите id как в наблюдении, "
                    f"например wooden_pickaxe или minecraft:oak_log{hint}")

    def _world_rules(self, name: str, params: dict, path: str, inventory) -> None:
        context = self.context
        if name == "goto":
            if context.position is None:
                self._error(f"{path}: позиция духа в наблюдении неизвестна, расстояние goto не проверить")
            else:
                distance = math.hypot(params["x"] - context.position[0], params["z"] - context.position[2])
                if distance > self.policy.max_goto_distance:
                    self._error(f"{path}.params: цель goto в {distance:.0f} блоках по горизонтали от духа, допустимо не больше "
                                f"{self.policy.max_goto_distance}: выберите точку ближе или идите несколькими планами")
        for script, param in self.policy.observed_block_params:
            value = params.get(param)
            unknown = (isinstance(value, str) and context.observed_blocks is not None
                       and normalize_block(value) not in context.observed_blocks)
            if script == name and unknown:
                shown = ", ".join(sorted(context.observed_blocks)[:12]) or "в наблюдении нет блоков"
                self._error(f"{path}.params.{param}: блока {value!r} нет в последнем наблюдении (есть, например: {shown})")
            elif script == name and isinstance(value, str) and normalize_block(value) not in self.policy.natural_blocks:
                self._error(f"{path}.params.{param}: блок {value!r} в природе не встречается, его сделал игрок "
                            f"(природные блоки перечислены в natural_blocks файла policy.json): собирайте только "
                            f"руду, камень, землю, песок, гравий, глину, брёвна, листву и растения — доски, стекло, "
                            f"кирпич и прочие постройки игрока не разбираются")
        self._pickaxe_rule(name, params, path, inventory)

    def _pickaxe_rule(self, name: str, params: dict, path: str, inventory) -> None:
        level = required_pickaxe(name, params)
        if level is None:
            return
        if inventory is None:
            self.warnings.append(f"{path}: в наблюдении нет списка предметов духа — наличие кирки нужного уровня "
                                 f"для {name} не проверить (предупреждение)")
            return
        best = max((PICKAXE_LEVELS.get(item, 0) for item in inventory), default=0)
        if best >= level:
            return
        have = sorted(item for item in inventory if item.endswith("_pickaxe"))
        if have:
            self._error(f"{path}: шаг {name} требует {PICKAXE_REQUIREMENT[level]}, а в инвентаре духа только "
                        f"{', '.join(have)} (наблюдение): возьмите кирку выше уровнем или смените план")
        else:
            self._error(f"{path}: шаг {name} требует {PICKAXE_REQUIREMENT[level]}, но в инвентаре духа нет ни одной "
                        f"кирки (наблюдение): сначала добудьте или скрафтите кирку, потом этот шаг")

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

    def _steps(self, node: dict, path: str, depth: int, inventory) -> frozenset | None:
        steps = self._steps_list(node, path, depth)
        if steps is None:
            return inventory
        current = inventory
        for index, child in enumerate(steps):
            current = self._node(child, f"{path}.steps[{index}]", depth + 1, current)
        return current

    def _steps_list(self, node: dict, path: str, depth: int) -> list | None:
        steps = node.get("steps")
        if not isinstance(steps, list) or not steps:
            self._error(f"{path}.steps: должен быть непустым списком узлов, получено {_describe(steps)}")
            return None
        if depth == 1 and len(steps) > self.limits.max_root_steps:
            self._error(f"{path}.steps: в корне {len(steps)} шагов, допустимо не больше {self.limits.max_root_steps}")
        return steps

    def _sequence(self, node: dict, path: str, depth: int, inventory) -> frozenset | None:
        return self._steps(node, path, depth, inventory)

    def _fallback(self, node: dict, path: str, depth: int, inventory) -> frozenset | None:
        return self._steps(node, path, depth, inventory)

    def _parallel(self, node: dict, path: str, depth: int, inventory) -> frozenset | None:
        if node.get("mode") not in PARALLEL_MODES:
            self._error(f"{path}.mode: допустимы {', '.join(PARALLEL_MODES)}, получено {_describe(node.get('mode'))}")
        steps = self._steps_list(node, path, depth)
        if steps is None:
            return inventory
        result = inventory
        for index, child in enumerate(steps):
            outcome = self._node(child, f"{path}.steps[{index}]", depth + 1, inventory)
            if result is not None and outcome is not None:
                result = result | outcome
        return result

    def _repeat(self, node: dict, path: str, depth: int, inventory) -> frozenset | None:
        times = node.get("times")
        if isinstance(times, bool) or not isinstance(times, int):
            self._error(f"{path}.times: ожидалось целое число, получено {_describe(times)}")
        elif not (self.limits.repeat_min <= times <= self.limits.repeat_max):
            self._error(f"{path}.times: значение {times} вне диапазона {self.limits.repeat_min}..{self.limits.repeat_max}")
        if "step" not in node:
            self._error(f"{path}.step: у узла repeat нет шага, нечего повторять")
            return inventory
        return self._node(node["step"], f"{path}.step", depth + 1, inventory)

    def _condition(self, node: dict, path: str, depth: int, inventory) -> frozenset | None:
        check = node.get("check")
        schema = self.catalog.conditions.get(check) if isinstance(check, str) else None
        if schema is None:
            known = ", ".join(sorted(self.catalog.conditions)) or "проверок нет"
            self._error(f"{path}: узел condition: проверка {_describe(check)} не найдена в каталоге условий "
                        f"(поле conditions в /v1/capabilities: {known})")
        else:
            self._params(node.get("params"), schema, path, f"проверки {check!r}")
        then_out = inventory
        if "then" not in node:
            self._error(f"{path}.then: у узла condition нет ветки then")
        else:
            then_out = self._node(node["then"], f"{path}.then", depth + 1, inventory)
        else_out = inventory
        if node.get("else") is not None:
            else_out = self._node(node["else"], f"{path}.else", depth + 1, inventory)
        if then_out is None or else_out is None:
            return inventory
        return then_out | else_out
