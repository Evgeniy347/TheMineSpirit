"""Модель графа потребностей поселения — перенос правил Java (GraphPlanner) один в один.

Источник истины: src/main/java/com/theminespirit/core/board/graph/*.java
Данные: mod/src/main/resources/data/theminespirit/board/graph.json
(выгрузка: ./gradlew graphExport -> TestResults/graph-export/graph.json)
"""

import copy
import fnmatch
import json
import math
import os

MAX_ITERATIONS = 200
EPSILON = 1e-6

TOOL = "tool:"
BUILDING = "building:"
EVENT = "event:"
EXPLORE = "explore:"
STATION = "station:"
ORDER = "order:"
AT = "@"

BUILT = "BUILT"
PLANNED = "PLANNED"
ABSENT = "ABSENT"

HERD_CULL_BOOST = 70
EXPLORE_TICKS = 2400


STORE_MINIMUM = "store:minimum"
STORE_TRANSFER = "store:transfer"
STORE_SMELT = "store:smelt"
STORE_SPACE = "store:space"
STORE_SORT = "store:sort"
STORE_DELIVER = "store:carry:deliver"
STORE_MOVE = "store:carry:transfer"
STORE_SMELT_LOAD = "store:smelt:load"
STORE_SMELT_TAKE = "store:smelt:take"
STORE_CHESTS = "store:chests"
STORE_SORTED = "store:sorted"
STORE_DEFAULT_BASE = 50
STORE_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
WAREHOUSE_CATEGORIES = os.path.join(STORE_ROOT, "mod", "src", "main", "resources", "data",
                                    "theminespirit", "inventory", "warehouse_categories.json")
_CATEGORIES = None


def warehouse_categories():
    global _CATEGORIES
    if _CATEGORIES is None:
        if not os.path.isfile(WAREHOUSE_CATEGORIES):
            _CATEGORIES = []
        else:
            with open(WAREHOUSE_CATEGORIES, "r", encoding="utf-8") as handle:
                _CATEGORIES = list(json.load(handle).get("categories") or [])
    return _CATEGORIES


def category_of(item):
    qualified = item if ":" in item else "minecraft:" + item
    for category in warehouse_categories():
        for pattern in category.get("match") or []:
            full = pattern if "*" == pattern or ":" in pattern else "minecraft:" + pattern
            if fnmatch.fnmatchcase(qualified, full):
                return category.get("name", "")
    return ""

NAMESPACE = "minecraft:"
ROOT_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FRAGMENT_DIR = os.path.join(
    ROOT_DIR, "mod", "src", "main", "resources", "data", "theminespirit", "board", "graph.d")
ARMOR_SLOTS = ("HEAD", "CHEST", "LEGS", "FEET")
ARMOR_SUFFIXES = {
    "_helmet": "HEAD",
    "_chestplate": "CHEST",
    "_leggings": "LEGS",
    "_boots": "FEET",
}
TOOL_KINDS = {"pickaxe", "axe", "shovel", "sword", "hoe"}


def _fragment(name):
    path = os.path.join(FRAGMENT_DIR, name)
    try:
        with open(path, "r", encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, ValueError):
        return {}


def _gear_settings():
    data = _fragment("gear.json")
    kits = {}
    for role, value in (data.get("gearKits") or {}).items():
        kits[role] = {
            "activity": value.get("activity", role),
            "tools": list(value.get("tools") or []),
        }
    required = {int(key): dict(value) for key, value in (data.get("gearRequired") or {}).items()}
    worn = data.get("gearWorn") or {}
    return {
        "tiers": list(data.get("gearTiers") or []),
        "armorTiers": list(data.get("gearArmorTiers") or []),
        "wornPercent": int(worn.get("percent", 20)),
        "armorStage": int(worn.get("armorStage", 2)),
        "required": required,
        "kits": kits,
    }


def _required_at(gear, stage):
    result = {}
    for step in range(1, stage + 1):
        result.update(gear["required"].get(step, {}))
    return result


def _short_id(item):
    return item[len(NAMESPACE):] if item.startswith(NAMESPACE) else item


_WALL_ITEMS = {
    "wall_torch": "torch",
    "soul_wall_torch": "soul_torch",
    "redstone_wall_torch": "redstone_torch",
    "wall_sign": "sign",
    "wall_hanging_sign": "hanging_sign",
    "wall_banner": "banner",
}


def _block_item(block):
    name = _short_id(block)
    if name == "air":
        return None
    return _WALL_ITEMS.get(name, name)


def _worn(durability, maximum, percent):
    return maximum > 0 and int(durability) * 100 // int(maximum) <= percent


def _best_tools(spirit):
    best = {}
    for tool in spirit.get("tools") or []:
        kind = tool.get("kind")
        if kind is not None:
            best[kind] = max(best.get(kind, -1), int(tool.get("tier", -1)))
    return best


def _tool_kind(name):
    split = name.rfind("_")
    if split <= 0:
        return None
    kind = name[split + 1:]
    return kind if kind in TOOL_KINDS else None


def _tool_tier(name):
    split = name.rfind("_")
    return "" if split <= 0 else name[:split].lower()


def _armor_slot(name):
    for suffix, slot in ARMOR_SUFFIXES.items():
        if name.endswith(suffix):
            return slot
    return None


def _armor_tier(name):
    split = name.find("_")
    return name if split <= 0 else name[:split].lower()


def _armor_ranks(spirit, gear):
    ranks = {}
    for armor in spirit.get("armor") or []:
        slot = armor.get("slot")
        rank = gear["armorTiers"].index(_armor_tier(_short_id(armor.get("item", "")))) \
            if _armor_tier(_short_id(armor.get("item", ""))) in gear["armorTiers"] else -1
        ranks[slot] = max(ranks.get(slot, -1), rank)
    return ranks


def _better_in_chests(spirit, chests, gear):
    tools = _best_tools(spirit)
    armor = _armor_ranks(spirit, gear)
    for chest in chests:
        for item in (chest.get("contents") or {}):
            name = _short_id(item)
            kind = _tool_kind(name)
            if kind is not None:
                tier = _tool_tier(name)
                rank = gear["tiers"].index(tier) if tier in gear["tiers"] else -1
                if rank > tools.get(kind, -1):
                    return True
            slot = _armor_slot(name)
            if slot is not None:
                tier = _armor_tier(name)
                rank = gear["armorTiers"].index(tier) if tier in gear["armorTiers"] else -1
                if rank > armor.get(slot, -1):
                    return True
    return False


def _life_events(state):
    """LifeEvents.events: угрозы и их цель поднимают полосу жизни."""
    threats = state.threats
    events = []
    if threats:
        events.append("life_threat")
    if any(threat.get("targetKind") == "SPIRIT" for threat in threats):
        events.append("life_threat_spirit")
    if any(threat.get("targetKind") == "PLAYER" for threat in threats) or any(
            weak_player(player, getattr(state, "life", None)) for player in getattr(state, "players", ())):
        events.append("life_threat_player")
    return events


def weak_player(player, life=None):
    life = life or DEFAULT_LIFE
    maximum = float(player.get("maxHealth", player.get("health", 0)) or 0)
    if maximum <= 0:
        return False
    return (float(player.get("health", 0)) / maximum < float(life.get("weakHealth", WEAK_HEALTH))
            or int(player.get("food", 20)) <= int(life.get("weakFood", WEAK_FOOD)))


def _gear_events(state):
    gear = _gear_settings()
    required = _required_at(gear, state.stage)
    events = []
    for spirit in state.spirit_facts.values():
        if any(_worn(tool.get("durability", 0), tool.get("maxDurability", 0), gear["wornPercent"])
               for tool in spirit.get("tools") or []):
            events.append("tool_worn")
        if any(_worn(armor.get("durability", 0), armor.get("maxDurability", 0), gear["wornPercent"])
               for armor in spirit.get("armor") or []):
            events.append("armor_worn")
        if _better_in_chests(spirit, state.chest_facts, gear):
            events.append("gear_equip_best")
        best = _best_tools(spirit)
        for kind, tier in required.items():
            rank = gear["tiers"].index(tier) if tier in gear["tiers"] else -1
            if rank >= 0 and best.get(kind, -1) < rank:
                events.append("gear_craft_" + kind)
        if state.stage >= gear["armorStage"] and len({armor.get("slot") for armor in spirit.get("armor") or []}) < len(ARMOR_SLOTS):
            events.append("gear_craft_armor")
    for role, kit in gear["kits"].items():
        active = any(kit["activity"] == spirit.get("activity") for spirit in state.spirit_facts.values())
        if not active:
            continue
        lacks = not kit["tools"] or any(
            required.get(kind) is not None
            and _best_tools(spirit).get(kind, -1)
            < (gear["tiers"].index(required[kind]) if required[kind] in gear["tiers"] else -1)
            for spirit in state.spirit_facts.values()
            if kit["activity"] == spirit.get("activity")
            for kind in kit["tools"])
        if lacks:
            events.append("gear_kit_" + role)
    return events



class Flow:
    def __init__(self):
        self.index = {}
        self.links = []
        self.roots = {}
        self.locals = {}

    def node(self, key):
        if key not in self.index:
            self.index[key] = len(self.index)
        return self.index[key]

    def root(self, key, value):
        self.node(key)
        self.roots[key] = self.roots.get(key, 0.0) + value

    def link(self, source, target, factor):
        self.node(source)
        self.node(target)
        self.links.append((source, target, factor))

    def local(self, key, value):
        self.node(key)
        self.locals[key] = self.locals.get(key, 0.0) + value

    def keys(self):
        return list(self.index.keys())

    def solve(self):
        size = len(self.index)
        base = [0.0] * size
        for key, value in self.roots.items():
            base[self.index[key]] += value
        sources = [self.index[a] for a, _b, _f in self.links]
        targets = [self.index[b] for _a, b, _f in self.links]
        factors = [f for _a, _b, f in self.links]
        value = list(base)
        for _ in range(MAX_ITERATIONS):
            nxt = list(base)
            for i in range(len(sources)):
                nxt[targets[i]] += factors[i] * value[sources[i]]
            delta = 0.0
            for i in range(size):
                d = abs(nxt[i] - value[i])
                if d > delta:
                    delta = d
            value = nxt
            if delta < EPSILON:
                break
        return {key: value[idx] + self.locals.get(key, 0.0) for key, idx in self.index.items()}


def priority(score, maximum):
    if score <= 0 or maximum <= 0:
        return 0
    return int(min(maximum, math.floor(maximum * (1.0 - math.exp(-score / maximum)) + 0.5)))


DAMAGE_WINDOW_TICKS = 200
THREAT_RADIUS = 16.0
FULL_PRESSURE_MOBS = 1.5
PRESSURE_BASE = 0.4
PRESSURE_WOUND = 0.7
HURT_SHARE = 0.2
INJURY_WOUND = 0.8
INJURY_HURT = 0.4
WEAK_HEALTH = 0.35
WEAK_FOOD = 6
DEFAULT_LIFE = {
    "fullPressureMobs": FULL_PRESSURE_MOBS,
    "pressureBase": PRESSURE_BASE,
    "pressureWound": PRESSURE_WOUND,
    "hurtShare": HURT_SHARE,
    "injuryWound": INJURY_WOUND,
    "injuryHurt": INJURY_HURT,
    "threatRadius": THREAT_RADIUS,
    "damageWindowTicks": DAMAGE_WINDOW_TICKS,
    "weakHealth": WEAK_HEALTH,
    "weakFood": WEAK_FOOD,
}


def _life(state, name):
    return float((getattr(state, "life", None) or DEFAULT_LIFE).get(name, DEFAULT_LIFE[name]))


def _clamp01(value):
    return max(0.0, min(1.0, value))


def _spirit_deficit(spirit):
    if spirit is None:
        return 0.0
    maximum = float(spirit.get("maxHealth", spirit.get("health", 0)))
    if maximum <= 0:
        return 0.0
    return _clamp01(1.0 - float(spirit.get("health", 0)) / maximum)


def _spirit_hurt(spirit):
    if spirit is None:
        return 0.0
    maximum = float(spirit.get("maxHealth", spirit.get("health", 0)))
    hurt = spirit.get("recentHurt")
    if maximum <= 0 or not hurt:
        return 0.0
    freshness = _clamp01(1.0 - int(hurt.get("ticksAgo", 0)) / float(DAMAGE_WINDOW_TICKS))
    return _clamp01(float(hurt.get("amount", 0)) / maximum) * freshness


def _threat_pressure(threats, match, life=None):
    radius = float((life or DEFAULT_LIFE).get("threatRadius", THREAT_RADIUS))
    total = sum(_clamp01(1.0 - int(threat.get("distance", 0)) / radius)
                for threat in threats if match(threat))
    return min(1.0, total / float((life or DEFAULT_LIFE).get("fullPressureMobs", FULL_PRESSURE_MOBS)))


def life_risk(state, spirit_id):
    """LifeThreats.risk: здоровье, свежий урон и мобы, сагренные именно на этого духа."""
    life = getattr(state, "life", None) or DEFAULT_LIFE
    spirit = state.spirit_facts.get(spirit_id)
    deficit = _spirit_deficit(spirit)
    hurt = _spirit_hurt(spirit)
    pressure = _threat_pressure(
        state.threats,
        lambda threat: threat.get("targetKind") == "SPIRIT"
        and (threat.get("target") is None or threat.get("target") == spirit_id),
        life)
    return _clamp01(pressure * (float(life.get("pressureBase", PRESSURE_BASE))
                                + float(life.get("pressureWound", PRESSURE_WOUND)) * deficit)
                    + float(life.get("hurtShare", HURT_SHARE)) * min(1.0, hurt))


def life_severity(state, event):
    """LifeThreats.severity: опасность события жизни из фактов мира."""
    def spirit_threat():
        targets = set(state.spirit_facts)
        any_spirit = False
        for threat in state.threats:
            if threat.get("targetKind") != "SPIRIT":
                continue
            any_spirit = True
            if threat.get("target") is not None:
                targets.add(threat.get("target"))
        if not targets and any_spirit:
            targets.add("")
        return max((life_risk(state, target) for target in targets), default=0.0)

    life = getattr(state, "life", None) or DEFAULT_LIFE

    def player_threat():
        pressure = _threat_pressure(state.threats, lambda threat: threat.get("targetKind") == "PLAYER", life)
        weak = 0.0
        for player in getattr(state, "players", ()):
            maximum = float(player.get("maxHealth", player.get("health", 0)) or 0)
            if maximum <= 0:
                continue
            share = float(player.get("health", 0)) / maximum
            if share >= float(life.get("weakHealth", WEAK_HEALTH))                     and int(player.get("food", 20)) > int(life.get("weakFood", WEAK_FOOD)):
                continue
            hungry = 0.0
            if int(player.get("food", 20)) <= int(life.get("weakFood", WEAK_FOOD)):
                hungry = _clamp01((int(life.get("weakFood", WEAK_FOOD)) - int(player.get("food", 20)) + 1)
                                  / float(int(life.get("weakFood", WEAK_FOOD)) + 1))
            weak = max(weak, max(_clamp01(1.0 - share), hungry))
        return max(pressure, weak)

    def injury():
        return max((_clamp01(float(life.get("injuryWound", INJURY_WOUND)) * _spirit_deficit(spirit)
                             + float(life.get("injuryHurt", INJURY_HURT)) * min(1.0, _spirit_hurt(spirit)))
                    for spirit in state.spirit_facts.values()), default=0.0)

    if event == "life_threat":
        return max(spirit_threat(), player_threat(), injury())
    if event == "life_threat_spirit":
        return spirit_threat()
    if event == "life_threat_player":
        return player_threat()
    if event == "life_injury":
        return injury()
    return 0.0


def life_priority(state, event, tasks, task, maximum):
    if maximum <= 0 or not tasks:
        return 0
    top = max(int(item.get("base", 0)) for item in tasks)
    severity = life_severity(state, event)
    if top <= 0 or severity <= 0:
        return 0
    share = _clamp01(float(task.get("base", 0)) / top)
    return min(maximum, max(0, int(math.floor(maximum * severity * share + 0.5))))


class Choice:
    """GraphChoice: правила выбора (штраф/добавка), нейтральные по умолчанию."""

    NAMES = ("farTrip", "slowWork", "duplicate", "conflict", "pileup", "strategicBoost",
             "bottleneckBoost", "idleStationBoost", "nightHome", "spaceFoundBoost",
             "spaceTight", "petAlone", "herdFull", "fieldFull", "failureBackoff",
             "failureTargets", "failureHalfLife", "failureSoft", "failureTargetRadius")
    NEUTRAL = {
        "farTrip": 1.0, "slowWork": 1.0, "duplicate": 1.0, "conflict": 1.0, "pileup": 1.0,
        "strategicBoost": 0.0, "bottleneckBoost": 0.0, "idleStationBoost": 0.0,
        "nightHome": 1.0, "spaceFoundBoost": 0.0, "spaceTight": 1.0,
        "petAlone": 1.0, "herdFull": 1.0, "fieldFull": 1.0, "failureBackoff": 0.5,
        "failureTargets": 2.0, "failureHalfLife": 3000.0, "failureSoft": 0.5,
        "failureTargetRadius": 6.0,
    }

    __slots__ = ("values",)

    def __init__(self, data=None):
        data = data or {}
        self.values = {name: float(data.get(name, self.NEUTRAL[name])) for name in self.NAMES}
        for name, value in self.values.items():
            if value < 0 or value != value or value in (float("inf"), float("-inf")):
                raise ValueError("Поправка выбора «%s» = %s: она неотрицательна и конечна" % (name, value))
        if self.values["failureTargets"] < 1:
            raise ValueError("Порог разных целей «память неудач» = %s: он не меньше 1"
                             % self.values["failureTargets"])
        if self.values["failureHalfLife"] <= 0:
            raise ValueError("Полураспад памяти неудач = %s тиков: он положителен"
                             % self.values["failureHalfLife"])

    def __getattr__(self, name):
        try:
            return self.values[name]
        except KeyError:
            raise AttributeError(name)


class Costs:
    """GraphCosts: длительность заданий, дорога, износ, ёмкость станций и поправки выбора."""

    __slots__ = ("default_ticks", "case_ticks", "way_ticks", "way_uses",
                 "ticks_per_block", "travel_scale_ticks", "tool_durability",
                 "station_capacity", "choice", "life")

    def __init__(self, data, ways):
        costs = data.get("costs")
        self.choice = Choice(data.get("choice"))
        self.life = dict(DEFAULT_LIFE)
        self.life.update(data.get("life") or {})
        if costs is None:
            self.default_ticks = 1
            self.case_ticks = {}
            self.way_ticks = {}
            self.way_uses = {}
            self.ticks_per_block = 0
            self.travel_scale_ticks = 1
            self.tool_durability = {}
            self.station_capacity = {}
            return
        self.default_ticks = int(costs.get("defaultTicks", 100))
        self.case_ticks = dict(costs.get("caseTicks") or {})
        self.way_ticks = {}
        self.way_uses = {}
        for way in ways:
            if "ticks" in way:
                self.way_ticks[way.get("id", "")] = int(way["ticks"])
            if "uses" in way:
                self.way_uses[way.get("id", "")] = int(way["uses"])
        self.ticks_per_block = int(costs.get("ticksPerBlock", 0))
        self.travel_scale_ticks = int(costs.get("travelScaleTicks", 1200))
        self.tool_durability = dict(costs.get("toolDurability") or {})
        self.station_capacity = dict(costs.get("stationCapacity") or {})

    def ticks(self, way_id, board_case):
        if way_id in self.way_ticks:
            return self.way_ticks[way_id]
        return int(self.case_ticks.get(board_case, self.default_ticks))

    def uses(self, way_id, yield_):
        return int(self.way_uses.get(way_id, max(1, yield_)))

    def travel(self, distance_blocks):
        return 2 * distance_blocks * self.ticks_per_block

    def travel_factor(self, travel_ticks):
        return self.travel_scale_ticks / (self.travel_scale_ticks + max(0, travel_ticks))

    def durability(self, tier):
        return int(self.tool_durability.get(tier, 2 ** 31 - 1))


def merge(manual, generated):
    """SupplyGraph.merge: ручное главнее, из сгенерированного добавляется недостающее."""
    merged = copy.deepcopy(manual)
    for field in ("items", "buildings", "groups"):
        source = generated.get(field)
        if not isinstance(source, dict):
            continue
        section = merged.get(field)
        if not isinstance(section, dict):
            section = {}
            merged[field] = section
        for key, value in source.items():
            if key not in section:
                section[key] = copy.deepcopy(value)
    source = generated.get("ways")
    if isinstance(source, list) and source:
        manual_makes = {way.get("makes") for way in (merged.get("ways") or [])}
        ways = merged.get("ways")
        if not isinstance(ways, list):
            ways = []
            merged["ways"] = ways
        for way in source:
            if way.get("makes") not in manual_makes:
                ways.append(copy.deepcopy(way))
    return merged


def merge_fragment(merged, fragment, origin):
    """GraphFragments: фрагмент добавляет только новые id, конфликт — ошибка с путём файла."""
    for field, value in fragment.items():
        if isinstance(value, dict):
            section = merged.get(field)
            if not isinstance(section, dict):
                section = {}
                merged[field] = section
            for key, node in value.items():
                if key in section:
                    raise ValueError(
                        "Файл '%s': id '%s.%s' уже есть в графе — фрагменты добавляют только новые узлы"
                        % (origin, field, key))
                section[key] = copy.deepcopy(node)
        elif isinstance(value, list):
            section = merged.get(field)
            if not isinstance(section, list):
                section = []
                merged[field] = section
            ids = {node.get("id") for node in section
                   if isinstance(node, dict) and node.get("id") is not None}
            texts = {node for node in section if isinstance(node, str)}
            for node in value:
                node_id = node.get("id") if isinstance(node, dict) else None
                if node_id is not None and node_id in ids:
                    raise ValueError(
                        "Файл '%s': id '%s.%s' уже есть в графе — фрагменты добавляют только новые узлы"
                        % (origin, field, node_id))
                if isinstance(node, str) and node in texts:
                    raise ValueError(
                        "Файл '%s': id '%s.%s' уже есть в графе — фрагменты добавляют только новые узлы"
                        % (origin, field, node))
                if node_id is not None:
                    ids.add(node_id)
                if isinstance(node, str):
                    texts.add(node)
                section.append(copy.deepcopy(node))
        else:
            if field in merged and merged[field] != value:
                raise ValueError(
                    "Файл '%s': id '%s' уже есть в графе — фрагменты добавляют только новые узлы"
                    % (origin, field))
            merged[field] = copy.deepcopy(value)
    return merged


class HerdCap:
    """HerdCap: предел стада числом (fixed) или долей на духа (perSpirit)."""

    __slots__ = ("fixed", "per_spirit")

    def __init__(self, value):
        if isinstance(value, dict):
            self.fixed = int(value["fixed"]) if "fixed" in value else None
            self.per_spirit = float(value["perSpirit"]) if "perSpirit" in value else None
        else:
            self.fixed = int(value)
            self.per_spirit = None
        if self.fixed is None and self.per_spirit is None:
            raise ValueError("Лимит стада без числа и без доли на духа: нечего сравнивать с поголовьем")

    def limit(self, spirits):
        if self.per_spirit is not None:
            return int(math.floor(self.per_spirit * spirits))
        return self.fixed


class Item:
    __slots__ = ("id", "xmin", "k", "base", "stage", "per_spirit")

    def __init__(self, node):
        self.id = node.get("id", "")
        self.xmin = int(node.get("xmin", 0))
        self.k = float(node.get("k", 1.0))
        self.base = int(node.get("base", 0))
        self.stage = int(node.get("stage", 1))
        self.per_spirit = bool(node.get("perSpirit", False))


class Building:
    __slots__ = ("id", "board_case", "stage", "base", "tool", "materials", "requires", "advice", "optional")

    def __init__(self, key, node):
        self.id = key
        self.board_case = node.get("case")
        self.stage = int(node.get("stage", 1))
        self.base = int(node.get("base", 0))
        self.tool = node.get("tool")
        self.materials = dict(node.get("materials") or {})
        self.requires = list(node.get("requires") or [])
        self.advice = list(node.get("advice") or [])
        self.optional = bool(node.get("optional", False))


class Way:
    __slots__ = ("id", "board_case", "makes", "yield_", "weight", "consumes",
                 "tool", "tier", "building", "world", "needs")

    def __init__(self, node):
        self.id = node.get("id", "")
        self.board_case = node.get("case")
        self.makes = node.get("makes", "")
        self.yield_ = int(node.get("yield", 1))
        self.weight = float(node.get("weight", 1.0))
        self.consumes = dict(node.get("consumes") or {})
        self.tool = node.get("tool")
        self.tier = node.get("tier")
        self.building = node.get("building")
        self.world = node.get("world")
        self.needs = dict(node.get("needs") or {})


class Graph:
    def __init__(self, data):
        self.raw = data
        self.decay = float(data.get("decay", 0.5))
        self.urgent_boost = int(data.get("urgentBoost", 0))
        self.build_ready_boost = int(data.get("buildReadyBoost", 0))
        self.build_start_share = float(data.get("buildStartShare", 1.0))
        self.max_priority = int(data.get("maxPriority", 100))
        self.work_priority = self.max_priority - 1 if self.max_priority > 0 else self.max_priority
        self.tiers = list(data.get("tiers") or [])
        kit = data.get("kit") or {}
        self.kit_tiers = dict(kit.get("tiers") or {})
        self.kit_stage_tiers = {int(k): dict(v) for k, v in (kit.get("stageTiers") or {}).items()}
        self.kit_base = int(kit.get("base", 0))
        self.kit_secondary_base = int(kit.get("secondaryBase", kit.get("base", 0)))
        storage = data.get("storage") or {}
        self.storage_min_free = int(storage.get("minFreePercent", 0))
        self.storage_building = storage.get("building")
        self.storage_base = int(storage.get("base", 0))
        self.items = {k: Item(dict(v, id=k)) for k, v in (data.get("items") or {}).items()}
        self.buildings = {k: Building(k, v) for k, v in (data.get("buildings") or {}).items()}
        self.ways = [Way(w) for w in (data.get("ways") or [])]
        self.edges = [dict(e) for e in (data.get("edges") or [])]
        self.events = {}
        for name, event in (data.get("events") or {}).items():
            self.events[name] = {
                "tasks": [dict(t) for t in (event.get("tasks") or [])],
                "push": dict(event.get("push") or {}),
                "advice": list(event.get("advice") or []),
                "life": bool(event.get("life", False)),
            }
        self._derive_repairs()
        self.groups = {k: list(v) for k, v in (data.get("groups") or {}).items()}
        self.triggers = [dict(t) for t in (data.get("triggers") or [])]
        self.herd_caps = {k: HerdCap(v) for k, v in (data.get("herdCaps") or {}).items()}
        self.unimplemented = set()
        for section in ("items", "buildings"):
            for key, value in (data.get(section) or {}).items():
                if not value.get("implemented", True):
                    self.unimplemented.add(key)
        for node in data.get("ways") or []:
            if not node.get("implemented", True):
                self.unimplemented.add(node.get("id", ""))
        self._producers = {}
        for way in self.ways:
            self._producers.setdefault(way.makes, []).append(way)
        self._way_by_id = {}
        for way in self.ways:
            self._way_by_id.setdefault(way.id, way)
        self.costs = Costs(data, data.get("ways") or [])

    def _derive_repairs(self):
        template = self.events.get("building_damaged")
        if not template or not template["tasks"]:
            return
        for building in self.buildings:
            name = "repair_" + building
            if name in self.events:
                continue
            self.events[name] = {
                "tasks": [dict(task, id="repair:" + building, building=building)
                          for task in template["tasks"]],
                "push": dict(template["push"]),
                "advice": list(template["advice"]),
                "life": bool(template["life"]),
            }

    @classmethod
    def load(cls, path):
        with open(path, "r", encoding="utf-8") as handle:
            return cls(json.load(handle))

    @classmethod
    def load_merged(cls, manual_path, recipes_path, fragments_dir=None):
        """SupplyGraph.loadDefault: graph.json + graph-recipes.json + все graph.d/*.json."""
        with open(manual_path, "r", encoding="utf-8") as handle:
            manual = json.load(handle)
        with open(recipes_path, "r", encoding="utf-8") as handle:
            generated = json.load(handle)
        merged = merge(manual, generated)
        if fragments_dir and os.path.isdir(fragments_dir):
            for name in sorted(os.listdir(fragments_dir)):
                if not name.endswith(".json"):
                    continue
                path = os.path.join(fragments_dir, name)
                with open(path, "r", encoding="utf-8") as handle:
                    merge_fragment(merged, json.load(handle), path)
        return cls(merged)

    def scaled(self, weights):
        """Копия графа с множителями весов в пределах, заданных поиском.

        weights = {"decay": m, "urgentBoost": m, "buildReadyBoost": m,
                   "items": {id: m}, "buildings": {id: m}, "ways": {id: m}}
        """
        import copy
        data = copy.deepcopy(self.raw)
        for key in ("decay", "urgentBoost", "buildReadyBoost"):
            factor = weights.get(key)
            if factor is None:
                continue
            if key == "decay":
                data[key] = float(self.decay) * factor
            else:
                data[key] = int(math.floor(float(data.get(key, 0)) * factor + 0.5))
        for item_id, factor in (weights.get("items") or {}).items():
            node = data.get("items", {}).get(item_id)
            if node is not None:
                node["base"] = int(math.floor(float(node.get("base", 0)) * factor + 0.5))
        for building_id, factor in (weights.get("buildings") or {}).items():
            node = data.get("buildings", {}).get(building_id)
            if node is not None:
                node["base"] = int(math.floor(float(node.get("base", 0)) * factor + 0.5))
        for way in data.get("ways", []):
            factor = (weights.get("ways") or {}).get(way.get("id", ""))
            if factor is not None:
                way["weight"] = float(way.get("weight", 1.0)) * factor
        for name, factor in (weights.get("choice") or {}).items():
            if name in Choice.NEUTRAL:
                section = data.setdefault("choice", {})
                section[name] = float(section.get(name, Choice.NEUTRAL[name])) * factor
        for event_name, factor in (weights.get("events") or {}).items():
            event = data.get("events", {}).get(event_name)
            if event is None:
                continue
            for task in event.get("tasks") or []:
                task["base"] = int(math.floor(float(task.get("base", 0)) * factor + 0.5))
        return Graph(data)

    def members(self, node):
        return self.groups.get(node, [])

    def is_group(self, node):
        return node in self.groups

    def implemented(self, node):
        return node not in self.unimplemented

    def rank(self, tier):
        try:
            return self.tiers.index(tier)
        except ValueError:
            return -1

    def way(self, way_id):
        return self._way_by_id.get(way_id)

    def producers(self, node):
        return self._producers.get(node, [])

    def is_tool(self, node):
        split = node.find("_")
        return split > 0 and node[:split] in self.tiers and node[split + 1:] in self.kit_tiers

    def kit_tier(self, kind, stage):
        tier = self.kit_tiers.get(kind, "wooden")
        for step in range(1, stage + 1):
            tier = self.kit_stage_tiers.get(step, {}).get(kind, tier)
        return tier

    def kind_names(self):
        return sorted(self.kit_tiers.keys())


class State:
    def __init__(self, data):
        self.stock = dict(data.get("stock") or {})
        self.stage = int(data.get("stage", 1))
        self.kits = {k: dict(v) for k, v in (data.get("kits") or {}).items()}
        self.buildings = dict(data.get("buildings") or {})
        self.materials = {k: dict(v) for k, v in (data.get("materials") or {}).items()}
        self.storage_free_percent = int(data.get("storageFreePercent", 0))
        self.events = list(data.get("events") or [])
        self.world = list(data.get("world") or [])
        self.distances = dict(data.get("distances") or {})
        self.stations = dict(data.get("stations") or {})
        self.orders = dict(data.get("orders") or {})
        self.facts = dict(data.get("facts") or {})
        self.spirit_facts = {k: dict(v) for k, v in (self.facts.get("spirits") or {}).items()}
        self.threats = list(self.facts.get("threats") or [])
        self.chest_facts = list(self.facts.get("chests") or [])
        self.resource_facts = list(self.facts.get("resources") or [])
        self.station_facts = dict(self.facts.get("stations") or {})
        self.building_facts = dict(self.facts.get("buildings") or {})
        self.strays = list(self.facts.get("strays") or [])
        self.terrain = dict(self.facts.get("terrain") or {})
        self.stage_reqs = dict(self.facts.get("stageReqs") or {})
        feedback = dict(self.facts.get("feedback") or {})
        self.clock = dict(feedback.get("clock") or {})
        self.player = dict(feedback.get("player") or {})
        self.players = list(self.facts.get("players") or [])
        self.failures = list(feedback.get("failures") or [])
        self._derive_world_events()

    def _derive_world_events(self):
        """GraphWorldEvents: входы участка добавляют узлы уборки и ремонта.

        terrain.natural — природный рельеф вне лотов; он не работа и события не поднимает.
        """
        derived = []
        if self.strays:
            derived.append("demolish_stray")
        if int(self.terrain.get("pits", 0)) > 0 or self.terrain.get("fill"):
            derived.append("backfill")
        if int(self.terrain.get("hanging", 0)) > 0 or self.terrain.get("floating"):
            derived.append("clear_floating")
        if int(self.terrain.get("heightDrop", 0)) > 0 or self.terrain.get("cut"):
            derived.append("level_lot")
        if int(self.terrain.get("traces", 0)) > 0:
            derived.append("restore_trace")
        for building_id, building in self.building_facts.items():
            if building.get("unknown", False):
                continue
            percent = int(building.get("auditPercent", 0))
            if (int(building.get("damage", 0)) > 0 or int(building.get("wrong", 0)) > 0
                    or 0 < percent < 100):
                derived.append("repair_" + building_id)
        derived.extend(_life_events(self))
        derived.extend(_gear_events(self))
        for name in derived:
            if name not in self.events:
                self.events.append(name)
        self._derive_feedback_tags()

    def _derive_feedback_tags(self):
        """MoreInputs/MoreRules: время суток и игрок из обратной связи мира."""
        player = self.player or {}
        clock = self.clock or {}
        if player.get("near", False):
            if "more:player_near" not in self.world:
                self.world.append("more:player_near")
            if player.get("holdingBlock", False) or player.get("usingItem", False) or player.get("digging", False):
                if "more:player_building" not in self.world:
                    self.world.append("more:player_building")
        if clock.get("duskSoon", False):
            for tag in ("more:dusk_soon", "more:bad_trip_conditions"):
                if tag not in self.world:
                    self.world.append(tag)
        if self.failures and "more:failures_repeat" not in self.world:
            self.world.append("more:failures_repeat")

    def copy(self):
        return State({
            "stock": self.stock,
            "stage": self.stage,
            "kits": self.kits,
            "buildings": self.buildings,
            "materials": self.materials,
            "storageFreePercent": self.storage_free_percent,
            "events": self.events,
            "world": self.world,
            "distances": self.distances,
            "stations": self.stations,
            "orders": self.orders,
            "facts": self.facts,
        })

    def spirits(self):
        return len(self.kits)

    def station_count(self, building):
        return int(self.stations.get(building, 1 if self.built(building) == BUILT else 0))

    def count(self, item):
        return int(self.stock.get(item, 0))

    def built(self, building):
        return self.buildings.get(building, ABSENT)

    def tier(self, spirit, kind):
        return int(self.kits.get(spirit, {}).get(kind, -1))

    def best_tier(self, kind):
        best = -1
        for kit in self.kits.values():
            value = int(kit.get(kind, -1))
            if value > best:
                best = value
        return best


def buildings_first(graph, tasks, ready):
    """GraphBuildOrder.buildingsFirst: постройки и их поставка выше прочих заданий."""
    supply = set()
    for task in tasks:
        if not task["id"].startswith(BUILDING) or not ready(task["makes"]):
            continue
        for item in task["consumes"]:
            if graph.is_group(item):
                supply.update(graph.members(item))
            else:
                supply.add(item)
    max_other = max((task["priority"] for task in tasks
                     if not task["id"].startswith(BUILDING) and task["makes"] not in supply), default=0)
    order = sorted({task["priority"] for task in tasks
                    if task["id"].startswith(BUILDING) and task["priority"] > 0})
    if not order or max_other >= graph.work_priority:
        return tasks
    start = max(max_other + 1, order[0])
    room = max(order[0], graph.work_priority - (len(order) - 1))
    start = min(start, room)
    step = max(1, (graph.work_priority - start) // (len(order) - 1)) if len(order) > 1 else 1
    band = {value: min(graph.work_priority, start + index * step)
            for index, value in enumerate(order)}
    max_supply = max((task["priority"] for task in tasks
                      if not task["id"].startswith(BUILDING) and task["makes"] in supply), default=0)
    shift = max(0, start - 1 - max_supply)
    result = []
    for task in tasks:
        if task["id"].startswith(BUILDING):
            priority = band.get(task["priority"], task["priority"]) if task["priority"] > 0 else 0
        elif task["makes"] in supply and task["consumes"]:
            priority = start
        elif task["makes"] in supply:
            priority = min(graph.work_priority, task["priority"] + shift)
        else:
            priority = task["priority"]
        if priority != task["priority"]:
            task = dict(task, priority=min(graph.work_priority, priority))
        result.append(task)
    return result


NIGHT_LEASH_RADIUS = 32


class Planner:
    def __init__(self, graph, state):
        if graph is None or state is None:
            raise ValueError("Граф и состояние обязательны")
        self.graph = graph
        self.state = state
        state.life = graph.costs.life
        state.events = list(state.events)
        self.flow = Flow()
        self.expanded = set()
        self.notices = {}
        self.chances = set()
        self.advice = []
        self._sourced = {}
        self._failure_index = None

    @staticmethod
    def threshold(item, state):
        stage = 1 if item.per_spirit else state.stage
        return int(math.ceil(item.xmin * state.spirits() * stage * item.k))

    def lacking(self, node):
        graph = self.graph
        state = self.state
        if graph.is_group(node):
            members = graph.members(node)
            if not members:
                return False
            first = graph.items.get(members[0])
            have = sum(state.count(member) for member in members)
            return first is not None and have < self.threshold(first, state)
        item = graph.items.get(node)
        if item is not None and item.stage <= state.stage:
            return state.count(node) < self.threshold(item, state)
        if graph.is_tool(node):
            split = node.find("_")
            return state.best_tier(node[split + 1:]) < graph.rank(node[:split])
        return False

    def count(self, node):
        if self.graph.is_group(node):
            return sum(self.state.count(member) for member in self.graph.members(node))
        return self.state.count(node)

    def available(self, way):
        state = self.state
        if way.world is not None and way.world not in state.world:
            return False
        if self.far_at_night(way):
            return False
        for item_id in way.consumes:
            item = self.graph.items.get(item_id)
            if item is not None and item.stage > state.stage:
                return False
        if way.building is None:
            return True
        building = self.graph.buildings.get(way.building)
        if building is None:
            return True
        planned = (not building.optional) or state.built(building.id) != ABSENT
        return building.stage <= state.stage and planned

    def far_at_night(self, way):
        if way.world is None or str(way.world).startswith("more:"):
            return False
        night = "night" in self.state.events or "more:dusk_soon" in self.state.world
        if not night:
            return False
        return int(self.state.distances.get(way.world, 0)) > NIGHT_LEASH_RADIUS

    def sourced(self, node):
        if node in self._sourced:
            return self._sourced[node]
        if self.graph.is_group(node):
            result = any(self.sourced(member) for member in self.graph.members(node))
        else:
            producers = self.graph.producers(node)
            result = True
            if producers:
                result = False
                for way in producers:
                    if not self.available(way):
                        continue
                    if all(self.count(item) > 0 or self.sourced_shallow(item) for item in way.consumes):
                        result = True
                        break
        self._sourced[node] = result
        return result

    def sourced_shallow(self, node):
        if self.graph.is_group(node):
            return any(self.sourced_shallow(member) for member in self.graph.members(node))
        return any(self.available(way) for way in self.graph.producers(node))

    def housed(self, way):
        return way.building is None or self.state.built(way.building) == BUILT

    def stocked(self, needs):
        return all(self.count(item) >= value for item, value in needs.items())

    def share(self, needs):
        total = sum(needs.values())
        if total == 0:
            return 1.0
        have = sum(min(value, max(0, self.count(item))) for item, value in needs.items())
        return have / total

    def materials(self, building):
        known = self.graph.buildings.get(building)
        material = self.state.materials.get(building)
        if material is not None:
            return material
        fact = self.state.building_facts.get(building) or {}
        missing = fact.get("missing") or {}
        if missing and not fact.get("unknown", False):
            items = {}
            for block, count in missing.items():
                item = _block_item(block)
                if item is not None and item in self.graph.items:
                    items[item] = items.get(item, 0) + int(count)
            if items:
                return items
        return known.materials if known is not None else {}

    def ready(self, building):
        known = self.graph.buildings.get(building)
        return known is not None and self.share(known.materials) >= self.graph.build_start_share

    def node_weight(self, node):
        item = self.graph.items.get(node)
        if item is not None:
            return max(1, item.base)
        if self.graph.is_group(node):
            return max((self.graph.items[member].base for member in self.graph.members(node)
                        if member in self.graph.items), default=40)
        return 40

    def dig_out(self):
        for spirit in self.state.spirit_facts.values():
            trapped = spirit.get("trapped", False)
            stuck = spirit.get("stuck", False)
            if not trapped and not stuck:
                continue
            if stuck:
                self.push(None, "rescue", self.node_weight("rescue"))
            if trapped:
                cause = spirit.get("trap")
                node = "pillar_open" if cause == "pit" else "bridge_open" if cause == "water" else "dig_open"
                self.push(None, node, self.node_weight(node))
            self.push(None, "trail_clean", self.node_weight("trail_clean"))

    def store_roots(self):
        graph = self.graph
        state = self.state
        if STORE_MINIMUM not in graph.items:
            return
        for item, have in self.store_shortages().items():
            extra = graph.urgent_boost if have == 0 else 0
            self.push(None, item, self.store_base(item) + extra)
        deliver, move = self.store_transfer()
        if deliver and STORE_TRANSFER in graph.items:
            self.push(None, STORE_DELIVER, self.store_base(STORE_DELIVER))
        if move and STORE_TRANSFER in graph.items:
            self.push(None, STORE_MOVE, self.store_base(STORE_MOVE))
        load, take = self.store_smelt()
        if load and STORE_SMELT in graph.items:
            self.push(None, STORE_SMELT_LOAD, self.store_base(STORE_SMELT_LOAD))
        if take and STORE_SMELT in graph.items:
            self.push(None, STORE_SMELT_TAKE, self.store_base(STORE_SMELT_TAKE))
        if state.storage_free_percent < graph.storage_min_free and STORE_SPACE in graph.items:
            if state.built("chests") == BUILT:
                self.push(None, STORE_CHESTS, self.store_base(STORE_CHESTS))
            else:
                self.push(None, BUILDING + "chests", self.store_base(STORE_CHESTS))
        if self.store_mixed_chest() and STORE_SORT in graph.items:
            self.push(None, STORE_SORTED, self.store_base(STORE_SORTED))

    def store_shortages(self):
        reqs = self.state.stage_reqs
        minimum = reqs.get("minimum") or {}
        actual = reqs.get("actual") or {}
        shortages = {}
        for item, target in minimum.items():
            if not self.store_known(item):
                continue
            have = int(actual.get(item, 0))
            if have < int(target):
                shortages[item] = have
        return shortages

    def store_known(self, node):
        graph = self.graph
        return (node in graph.items or graph.is_group(node) or graph.is_tool(node)
                or bool(graph.producers(node)))

    def store_base(self, node):
        item = self.graph.items.get(node)
        if item is not None:
            return item.base
        if self.graph.is_group(node):
            members = self.graph.members(node)
            return self.store_base(members[0]) if members else STORE_DEFAULT_BASE
        return STORE_DEFAULT_BASE

    def store_transfer(self):
        facts = self.state
        carried = any((spirit.get("inventory") or {}) for spirit in facts.spirit_facts.values())
        chests = facts.chest_facts
        free = any(int(chest.get("freeSlots", 0)) > 0 for chest in chests)
        move = False
        for index, source in enumerate(chests):
            if not (source.get("contents") or {}) or int(source.get("freeSlots", 0)) > 0:
                continue
            for other, target in enumerate(chests):
                if other != index and int(target.get("freeSlots", 0)) > 0:
                    move = True
                    break
        return carried and free, move

    def store_smelt(self):
        load = False
        take = False
        for station in self.state.station_facts.values():
            if station.get("type") != "furnace":
                continue
            if station.get("output") is not None:
                take = True
            elif not station.get("busy"):
                load = True
        return load, take

    def store_mixed_chest(self):
        for chest in self.state.chest_facts:
            found = set()
            for item in (chest.get("contents") or {}):
                found.add(category_of(item))
                if len(found) > 1:
                    return True
        return False

    def push(self, source, node, amount):
        if amount == 0:
            return
        if source is None:
            self.flow.root(node, amount)
        else:
            self.flow.link(source, node, amount)
        self.expand(node)

    def push_tool(self, source, kind, amount):
        spirits = self.state.spirits()
        for spirit in self.state.kits:
            self.push(source, TOOL + kind + AT + spirit, amount / max(1, spirits))

    def require_building(self, source, building_id, amount, why):
        building = self.graph.buildings.get(building_id)
        if building is None or self.state.built(building_id) == ABSENT or building.stage > self.state.stage:
            self.notices.setdefault(building_id, {
                "building": building_id,
                "reason": "нужна постройка " + building_id + " (" + why + "), её нет в плане",
            })
            return
        if self.audit_settles(building_id):
            return
        self.push(source, BUILDING + building_id, amount)

    def audit_settles(self, building_id):
        """Аудит решает судьбу постройки: известный прогресс или неизвестность — не полная стройка."""
        if self.state.built(building_id) == BUILT:
            return False
        fact = self.state.building_facts.get(building_id)
        if fact is None:
            return False
        return (bool(fact.get("unknown", False)) or int(fact.get("auditPercent", 0)) > 0
                or not bool(fact.get("lotReady", True)))

    def roots(self):
        graph = self.graph
        state = self.state
        for item in graph.items.values():
            if item.stage > state.stage:
                continue
            have = self.count(item.id)
            if have < self.threshold(item, state) and self.sourced(item.id):
                self.push(None, item.id, item.base + (graph.urgent_boost if have < item.xmin else 0))
        for spirit in list(state.kits.keys()):
            for kind in graph.kind_names():
                target = graph.rank(graph.kit_tier(kind, state.stage))
                tier = state.tier(spirit, kind)
                if tier < target:
                    base = graph.kit_secondary_base if state.best_tier(kind) >= target else graph.kit_base
                    self.push(None, TOOL + kind + AT + spirit, base + (graph.urgent_boost if tier < 0 else 0))
        for building in graph.buildings.values():
            if not building.optional and building.stage <= state.stage and state.built(building.id) != BUILT:
                self.require_building(None, building.id, building.base, "постройка этапа " + str(building.stage))
        for herd, cap in graph.herd_caps.items():
            if self.count(herd) > cap.limit(state.spirits()):
                for way in graph.ways:
                    if herd in way.consumes and self.available(way):
                        self.flow.local(way.id, HERD_CULL_BOOST)
                        self.expand_way(way.id, way)
        for trigger in graph.triggers:
            wanted = graph.buildings.get(trigger.get("build"))
            if wanted is not None and self.count(trigger.get("have")) > 0 and state.built(trigger["build"]) != BUILT:
                self.require_building(None, trigger["build"], wanted.base, "есть " + str(trigger.get("have")))
        if state.storage_free_percent < graph.storage_min_free:
            if state.built(graph.storage_building) == BUILT:
                self.notices.setdefault(graph.storage_building, {
                    "building": graph.storage_building,
                    "reason": "склад построен, но свободно " + str(state.storage_free_percent) + "%",
                })
            else:
                self.require_building(
                    None,
                    graph.storage_building,
                    graph.storage_base,
                    "на складе свободно " + str(state.storage_free_percent) + "% (< " + str(graph.storage_min_free) + "%)",
                )
        self.store_roots()
        self.dig_out()

    def expand(self, node):
        if node in self.expanded:
            return
        self.expanded.add(node)
        graph = self.graph
        state = self.state
        decay = graph.decay
        if node.startswith(TOOL):
            at = node.find(AT)
            kind = node[len(TOOL):at]
            spirit = node[at + 1:]
            for tier in graph.tiers:
                if graph.rank(tier) <= state.tier(spirit, kind):
                    continue
                above_kit = graph.rank(tier) > graph.rank(graph.kit_tier(kind, state.stage))
                for way in graph.producers(tier + "_" + kind):
                    key = way.id + AT + spirit
                    self.flow.link(node, key, way.weight)
                    if above_kit:
                        self.chances.add(key)
                    else:
                        self.expand_way(key, way)
        elif node.startswith(BUILDING):
            building_id = node[len(BUILDING):]
            building = graph.buildings.get(building_id)
            needs = self.materials(building_id)
            if building is not None and self.stocked(needs) and (
                    building.tool is None or state.best_tier(building.tool) >= 0):
                self.flow.local(node, graph.build_ready_boost)
            for item, value in needs.items():
                if self.count(item) < value:
                    self.push(node, item, decay)
            if building is not None:
                for required in building.requires:
                    if state.built(required) != BUILT:
                        self.require_building(node, required, decay, "нужна постройке " + building_id)
                if building.tool is not None and state.best_tier(building.tool) < 0:
                    self.push_tool(node, building.tool, decay)
        elif node.startswith(EVENT) or node.startswith(EXPLORE):
            return
        elif graph.is_group(node):
            for member in graph.members(node):
                if self.sourced(member):
                    self.push(node, member, 1.0)
        else:
            missing = []
            for way in graph.producers(node):
                if self.available(way):
                    self.flow.link(node, way.id, way.weight)
                    self.expand_way(way.id, way)
                if way.world is not None and way.world not in state.world \
                        and not way.world.startswith("more:") and way.world not in missing:
                    missing.append(way.world)
            if missing and not self.sourced(node):
                self.notices.setdefault(node, {
                    "node": node,
                    "reason": "нет источника " + node + " в округе (нет " + str(missing) + ")",
                })
                for resource in missing:
                    self.flow.link(node, EXPLORE + resource, decay)

    def expand_way(self, key, way):
        if key in self.expanded:
            return
        self.expanded.add(key)
        graph = self.graph
        state = self.state
        decay = graph.decay
        for item, value in way.consumes.items():
            if self.count(item) < value:
                self.push(key, item, decay)
        for item, value in way.needs.items():
            if self.count(item) < value:
                self.push(key, item, decay)
        if way.tool is not None and state.best_tier(way.tool) < graph.rank(way.tier or "wooden"):
            self.push_tool(key, way.tool, decay)
        if way.building is not None and state.built(way.building) != BUILT:
            self.require_building(key, way.building, decay, "нужна пути " + way.id)

    def event_consumes(self, task):
        building = task.get("building")
        if building is not None:
            material = self.state.materials.get(building)
            if material is not None:
                return dict(material)
        return dict(task.get("consumes") or {})

    def event_weight(self, task):
        base = int(task.get("base", 0))
        building = task.get("building")
        if building is None or building not in self.state.building_facts:
            return base
        fact = self.state.building_facts.get(building) or {}
        if fact.get("unknown", False):
            return base
        share = (100 - int(fact.get("auditPercent", 0))) / 100.0
        required = self.state.stage_reqs.get("minimum") or {}
        importance = 1.5 if building in required else 1.0
        return base + round(base * share * importance)

    def boost(self, node, weight, event_name):
        if node.startswith(TOOL):
            kind = node[len(TOOL):]
            target = self.graph.rank(self.graph.kit_tier(kind, self.state.stage))
            for spirit in self.state.kits:
                if self.state.tier(spirit, kind) < target:
                    self.push(None, TOOL + kind + AT + spirit, weight)
        elif node.startswith(BUILDING):
            building_id = node[len(BUILDING):]
            if self.state.built(building_id) != BUILT:
                self.require_building(None, building_id, weight, "событие " + event_name)
        elif self.lacking(node):
            self.push(None, node, weight)

    def events(self):
        for name in self.state.events:
            event = self.graph.events.get(name)
            if event is None:
                continue
            for task in event["tasks"]:
                key = EVENT + name + ":" + task.get("id", "")
                self.flow.root(key, self.event_weight(task))
                for item, value in self.event_consumes(task).items():
                    if self.count(item) < value:
                        self.push(key, item, self.graph.decay)
            for node, weight in event["push"].items():
                self.boost(node, weight, name)
            for rule in event["advice"]:
                self.advice.append((name, rule))

    def standing(self):
        for building in self.graph.buildings.values():
            if self.state.built(building.id) == BUILT:
                for rule in building.advice:
                    self.advice.append((building.id, rule))

    def orders(self):
        for item, lack in self.state.orders.items():
            if item in self.graph.items or self.graph.is_group(item):
                self.push(None, item, self.graph.max_priority)
            else:
                self.notices.setdefault(ORDER + item, {
                    "building": item,
                    "reason": "заказ игрока ждёт «" + item + "» ×" + str(lack)
                              + ", а граф не знает, как его добыть: добавьте предмет в граф",
                })

    def prepare(self):
        self.roots()
        self.events()
        self.orders()
        self.standing()

    def apply_edges(self, scores):
        changed = False
        for edge in self.graph.edges:
            if not self.lacking(edge.get("lack")):
                continue
            way_id = edge.get("way")
            active = False
            for key in list(scores.keys()):
                if (key == way_id or key.startswith(way_id + AT)) and scores.get(key, 0.0) > 0:
                    self.flow.local(key, edge.get("weight", 0))
                    active = True
            way = self.graph.way(way_id)
            if not active and edge.get("weight", 0) > 0 and way is not None \
                    and not self.graph.is_tool(way.makes) and self.available(way):
                self.flow.root(way.id, edge.get("weight", 0))
                self.expand_way(way.id, way)
                changed = True
            elif active:
                changed = True
        return changed

    def result(self, scores):
        tasks = []
        load = {}
        made = set()
        for key, raw in sorted(scores.items(), key=lambda entry: (-entry[1], entry[0])):
            if raw <= 0 or not self.is_task(key):
                continue
            repeat = self.makes(key) in made
            made.add(self.makes(key))
            value = raw * self.graph.costs.travel_factor(self.travel_ticks(key))
            value = self.choice(key, value, repeat)
            task = self.task(key, value)
            tasks.append(task)
            building = self.station(key)
            if building is not None and task["feasible"]:
                load[building] = max(load.get(building, 0.0), value)
        for building, value in load.items():
            extra = self.station_task(building, value, scores)
            if extra is not None:
                tasks.append(extra)
        return {"tasks": buildings_first(self.graph, tasks, self.ready),
                "notices": list(self.notices.values()), "advice": list(self.advice)}

    def makes(self, key):
        if key.startswith(BUILDING) or key.startswith(EVENT) or key.startswith(EXPLORE):
            return key
        at = key.find(AT)
        way = self.graph.way(key if at < 0 else key[:at])
        return way.makes if way is not None else key

    def choice(self, key, value, repeat):
        tuning = self.graph.costs.choice
        way = self.way_of(key)
        factor = 1.0
        bonus = 0.0
        if "more:strategic_goal" in self.state.world:
            bonus += tuning.strategicBoost
        if key.startswith(BUILDING):
            building = self.graph.buildings.get(key[len(BUILDING):])
            if "more4_space_found" in self.state.events:
                bonus += tuning.spaceFoundBoost
            if "more4_space_tight" in self.state.events:
                factor *= tuning.spaceTight
            if "stage_slow" in self.state.events and building is not None \
                    and building.stage == self.state.stage:
                bonus += tuning.bottleneckBoost
        elif way is not None:
            if "stage_slow" in self.state.events and self.stage_of(way) == self.state.stage:
                bonus += tuning.bottleneckBoost
            if "station_idle" in self.state.events and self.idle_station(way):
                bonus += tuning.idleStationBoost
            if "player_sleep" in self.state.events and self.travel(key) > 0:
                factor *= tuning.nightHome
            if "more4_tamed_pet" in self.state.events and way.board_case == "ACTIVITY_GUARD":
                factor *= tuning.petAlone
            if "more7_pen_full" in self.state.events and self.holds_herd(way):
                factor *= tuning.herdFull
            if "more7_field_full" in self.state.events and self.sows(way):
                factor *= tuning.fieldFull
        if repeat:
            if "more:work_duplicate" in self.state.world:
                factor *= tuning.duplicate
            if "work_conflict" in self.state.events:
                factor *= tuning.conflict
            if "more7_work_pileup" in self.state.events:
                factor *= tuning.pileup
        penalized, penalty = self.failure_stats(key)
        if penalty > 0:
            factor *= tuning.failureBackoff ** min(penalty, 3.0)
        return value * factor + bonus

    def failure_index(self):
        """Агрегат памяти неудач на один прогон плана: сумма по узлу считается один раз (GraphChoiceRules)."""
        if self._failure_index is not None:
            return self._failure_index
        index = {}
        for failure in self.state.failures:
            weight = float(failure.get("weight", failure.get("count", 0)))
            if weight <= 0:
                continue
            node = failure.get("node")
            entry = index.get(node)
            if entry is None:
                entry = [0.0, 0.0, set()]
                index[node] = entry
            cause = failure.get("cause", "OTHER")
            if cause in ("NO_PATH", "NOT_REACHED"):
                entry[0] += weight
                entry[2].add(failure.get("target", ""))
            elif cause == "OTHER":
                entry[1] += weight
        self._failure_index = index
        return index

    def failure_stats(self, key):
        """Память неудач: цель отдельно, узел — только когда провалились разные цели (GraphChoiceRules)."""
        base = key.split(AT, 1)[0]
        if base == "rescue_spirit" or base.startswith(EVENT + "life_"):
            return False, 0.0
        index = self.failure_index()
        unreachable, soft, node_targets = index.get(base, (0.0, 0.0, set()))
        targets = set(node_targets)
        if key != base:
            key_unreachable, key_soft, key_targets = index.get(key, (0.0, 0.0, set()))
            unreachable += key_unreachable
            soft += key_soft
            targets |= set(key_targets)
        tuning = self.graph.costs.choice
        penalized = len(targets) >= math.ceil(tuning.failureTargets)
        penalty = min(unreachable, 3.0) if penalized else 0.0
        penalty += min(soft * tuning.failureSoft, 3.0)
        return penalized, penalty

    def way_of(self, key):
        if key.startswith(EVENT):
            parts = key.split(":", 2)
            return self.graph.way(parts[2]) if len(parts) == 3 else None
        at = key.find(AT)
        return self.graph.way(key if at < 0 else key[:at])

    def stage_of(self, way):
        item = self.graph.items.get(way.makes)
        return -1 if item is None else item.stage

    def idle_station(self, way):
        building = way.building
        if building is None or building not in self.graph.costs.station_capacity:
            return False
        return any(not station.get("busy") and
                   (station.get("id") == building or station.get("type") == building)
                   for station in self.state.station_facts.values())

    def holds_herd(self, way):
        return way.board_case in ("BREED_ANIMALS", "CAPTURE_ANIMAL")

    def sows(self, way):
        return any(item == "#seeds" or item.endswith("_seeds") for item in way.consumes)

    def station(self, key):
        at = key.find(AT)
        way = self.graph.way(key if at < 0 else key[:at])
        if way is not None and way.building in self.graph.costs.station_capacity:
            return way.building
        return None

    def station_task(self, building, value, scores):
        busy = 0
        for key, score in scores.items():
            if score <= 0 or not self.is_task(key):
                continue
            if self.station(key) != building:
                continue
            if self.task(key, 0)["feasible"]:
                busy += 1
        capacity = self.graph.costs.station_capacity.get(building, 2 ** 31 - 1)
        if self.state.built(building) != BUILT or busy <= self.state.station_count(building) * capacity:
            return None
        known = self.graph.buildings.get(building)
        if known is None:
            raise KeyError("Станция " + building + " не описана среди построек графа")
        materials = self.materials(building)
        return {"id": STATION + building, "makes": building, "spirit": None,
                "priority": priority(value * self.graph.decay, self.graph.work_priority),
                "consumes": dict(materials), "feasible": self.stocked(materials),
                "chance": False, "implemented": self.graph.implemented(building),
                "ticks": self.graph.costs.ticks(STATION + building, known.board_case)}

    def travel(self, key):
        at = key.find(AT)
        way = self.graph.way(key if at < 0 else key[:at])
        if way is None or way.world is None:
            return 0
        return self.graph.costs.travel(self.state.distances.get(way.world, 0))

    def travel_ticks(self, key):
        raw = self.travel(key)
        if "more:bad_trip_conditions" not in self.state.world and not self.failure_stats(key)[0]:
            return raw
        return int(math.floor(raw * self.graph.costs.choice.farTrip + 0.5))

    def way_ticks(self, key, way):
        ticks = self.graph.costs.ticks(way.id, way.board_case) + self.travel_ticks(key)
        if "work_yield_low" in self.state.events:
            ticks = int(math.floor(ticks * self.graph.costs.choice.slowWork + 0.5))
        return ticks

    def is_task(self, key):
        if key.startswith(BUILDING) or key.startswith(EVENT) or key.startswith(EXPLORE):
            return True
        at = key.find(AT)
        return not key.startswith(TOOL) and self.graph.way(key if at < 0 else key[:at]) is not None

    def task(self, key, value):
        graph = self.graph
        state = self.state
        prio = priority(value, graph.work_priority)
        if key.startswith(EXPLORE):
            return {"id": key, "makes": key[len(EXPLORE):], "spirit": None, "priority": prio,
                    "consumes": {}, "feasible": True, "chance": False, "implemented": False,
                    "ticks": EXPLORE_TICKS}
        if key.startswith(EVENT):
            parts = key.split(":", 2)
            event = graph.events.get(parts[1], {"tasks": []})
            task = next((t for t in event["tasks"] if t.get("id") == parts[2]), None)
            if task is None:
                raise KeyError("Событие " + parts[1] + " задания не описано в графе")
            if event.get("life", False):
                prio = life_priority(state, parts[1], event["tasks"], task, graph.max_priority)
            consumes = self.event_consumes(task)
            building = task.get("building")
            fact = None if building is None else state.building_facts.get(building)
            target = building is None or state.built(building) == BUILT or (
                fact is not None and not fact.get("unknown", False)
                and int(fact.get("auditPercent", 0)) > 0)
            feasible = self.stocked(consumes) and target
            return {"id": key, "makes": task.get("id"), "spirit": None, "priority": prio,
                    "consumes": consumes, "feasible": feasible, "chance": False,
                    "implemented": bool(task.get("implemented", True)),
                    "ticks": graph.costs.ticks(key, task.get("case"))}
        if key.startswith(BUILDING):
            building_id = key[len(BUILDING):]
            building = graph.buildings.get(building_id)
            if building is None:
                raise KeyError("Постройка " + building_id + " задания не описана в графе")
            materials = self.materials(building_id)
            tooled = (building.tool is None or state.best_tier(building.tool) >= 0) and \
                all(state.built(required) == BUILT for required in building.requires)
            return {"id": key, "makes": building_id, "spirit": None, "priority": prio,
                    "consumes": dict(materials),
                    "feasible": tooled and self.share(building.materials) >= graph.build_start_share,
                    "chance": False, "implemented": graph.implemented(building_id),
                    "ticks": graph.costs.ticks(key, building.board_case)}
        at = key.find(AT)
        way_id = key if at < 0 else key[:at]
        way = graph.way(way_id)
        spirit = None if at < 0 else key[at + 1:]
        tooled = way.tool is None or state.best_tier(way.tool) >= graph.rank(way.tier or "wooden")
        feasible = tooled and self.housed(way) and self.available(way) \
            and self.stocked(way.consumes) and self.stocked(way.needs)
        return {"id": key, "makes": way.makes, "spirit": spirit, "priority": prio,
                "consumes": dict(way.consumes), "feasible": feasible,
                "chance": key in self.chances, "implemented": graph.implemented(way_id),
                "ticks": self.way_ticks(key, way)}

    def plan(self):
        self.prepare()
        scores = self.flow.solve()
        if self.apply_edges(scores):
            scores = self.flow.solve()
        return self.result(scores)


def plan(graph, state):
    return Planner(graph, state).plan()
