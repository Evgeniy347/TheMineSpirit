"""Модель графа потребностей поселения — перенос правил Java (GraphPlanner) один в один.

Источник истины: src/main/java/com/theminespirit/core/board/graph/*.java
Данные: src/main/resources/data/theminespirit/board/graph.json
(выгрузка: ./gradlew graphExport -> TestResults/graph-export/graph.json)
"""

import copy
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
WATCHER_OWNED = {"BUILD_WAREHOUSE", "BUILD_PEN"}


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
    if score <= 0:
        return 0
    return int(min(maximum, math.floor(maximum * (1.0 - math.exp(-score / maximum)) + 0.5)))


def band_priority(score, maximum, band):
    if score <= 0:
        return 0
    above = int(round(score - maximum))
    return maximum + max(1, min(band, above))


class Costs:
    """GraphCosts: длительность заданий, дорога, износ, ёмкость станций."""

    __slots__ = ("default_ticks", "case_ticks", "way_ticks", "way_uses",
                 "ticks_per_block", "travel_scale_ticks", "tool_durability",
                 "station_capacity")

    def __init__(self, data, ways):
        costs = data.get("costs")
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
            ids = {node.get("id") for node in section if isinstance(node, dict)}
            texts = {node for node in section if isinstance(node, str)}
            for node in value:
                if isinstance(node, dict) and node.get("id") in ids:
                    raise ValueError(
                        "Файл '%s': id '%s.%s' уже есть в графе — фрагменты добавляют только новые узлы"
                        % (origin, field, node.get("id")))
                if isinstance(node, str) and node in texts:
                    raise ValueError(
                        "Файл '%s': id '%s.%s' уже есть в графе — фрагменты добавляют только новые узлы"
                        % (origin, field, node))
                if isinstance(node, dict):
                    ids.add(node.get("id"))
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
                "band": int(event.get("band", 0)),
            }
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
    if not order or max_other >= graph.max_priority:
        return tasks
    start = max(max_other + 1, order[0])
    step = max(1, (graph.max_priority - start) // (len(order) - 1)) if len(order) > 1 else 1
    band = {value: min(graph.max_priority, start + index * step)
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
            priority = min(max(0, start - 1), task["priority"] + shift)
        else:
            priority = task["priority"]
        if priority != task["priority"]:
            task = dict(task, priority=min(graph.max_priority, priority))
        result.append(task)
    return result


class Planner:
    def __init__(self, graph, state):
        if graph is None or state is None:
            raise ValueError("Граф и состояние обязательны")
        self.graph = graph
        self.state = state
        self.flow = Flow()
        self.expanded = set()
        self.notices = {}
        self.chances = set()
        self.advice = []
        self._sourced = {}

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
        return self.state.materials.get(building, known.materials if known is not None else {})

    def ready(self, building):
        known = self.graph.buildings.get(building)
        return known is not None and self.share(known.materials) >= self.graph.build_start_share

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
        if building.board_case in WATCHER_OWNED:
            return
        self.push(source, BUILDING + building_id, amount)

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
                if way.world is not None and way.world not in state.world and way.world not in missing:
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
                self.flow.root(key, task.get("base", 0))
                for item, value in (task.get("consumes") or {}).items():
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
        for key, value in scores.items():
            if value > 0 and self.is_task(key):
                value *= self.graph.costs.travel_factor(self.travel(key))
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
                "priority": priority(value * self.graph.decay, self.graph.max_priority),
                "consumes": dict(materials), "feasible": self.stocked(materials),
                "chance": False, "implemented": self.graph.implemented(building),
                "ticks": self.graph.costs.ticks(STATION + building, known.board_case)}

    def travel(self, key):
        at = key.find(AT)
        way = self.graph.way(key if at < 0 else key[:at])
        if way is None or way.world is None:
            return 0
        return self.graph.costs.travel(self.state.distances.get(way.world, 0))

    def is_task(self, key):
        if key.startswith(BUILDING) or key.startswith(EVENT) or key.startswith(EXPLORE):
            return True
        at = key.find(AT)
        return not key.startswith(TOOL) and self.graph.way(key if at < 0 else key[:at]) is not None

    def task(self, key, value):
        graph = self.graph
        state = self.state
        prio = priority(value, graph.max_priority)
        if key.startswith(EXPLORE):
            return {"id": key, "makes": key[len(EXPLORE):], "spirit": None, "priority": prio,
                    "consumes": {}, "feasible": True, "chance": False, "implemented": False,
                    "ticks": EXPLORE_TICKS}
        if key.startswith(EVENT):
            parts = key.split(":", 2)
            event = graph.events.get(parts[1], {"tasks": []})
            band = int(event.get("band", 0))
            if band > 0:
                prio = band_priority(value, graph.max_priority, band)
            task = next((t for t in event["tasks"] if t.get("id") == parts[2]), None)
            if task is None:
                raise KeyError("Событие " + parts[1] + " задания не описано в графе")
            consumes = dict(task.get("consumes") or {})
            building = task.get("building")
            feasible = self.stocked(consumes) and (building is None or state.built(building) == BUILT)
            return {"id": key, "makes": task.get("id"), "spirit": None, "priority": prio,
                    "consumes": consumes, "feasible": feasible, "chance": False, "implemented": True,
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
                "ticks": graph.costs.ticks(way_id, way.board_case) + self.travel(key)}

    def plan(self):
        self.prepare()
        scores = self.flow.solve()
        if self.apply_edges(scores):
            scores = self.flow.solve()
        return self.result(scores)


def plan(graph, state):
    return Planner(graph, state).plan()
