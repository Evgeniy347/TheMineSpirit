"""Пакетная симуляция поселения на torch во времени.

Правила повторяют Java: GraphSimulation + GraphWorld
(src/test/java/com/theminespirit/core/board/graph/): тики заданий из costs, дорога,
износ инструментов, станции, события, этапы 1–3.
Миры идут пакетом, линейная система p = r + A·p решается на видеокарте.

Запуск:
    PYTHONIOENCODING=utf-8 py -3.9 sim_gpu.py --export ../../TestResults/graph-export
Сверка с Java: шаги, тики и порядок заданий по 5 базовым мирам
(TestResults/mutation/graph-trail.md, simulate_Baselines_WriteTrailReport).
"""

import argparse
import copy
import heapq
import math
import os
import sys

from graph_model import Graph, Planner, State, BUILT, PLANNED, ABSENT, EPSILON, MAX_ITERATIONS

try:
    import torch
except Exception:
    torch = None

LAST_STAGE = 3
IDLE_TICKS = 100
IDLE_LIMIT = 400
MAX_INT = 2 ** 31 - 1


class World:
    def __init__(self, graph, stock=None, kits=None, buildings=None, stage=1,
                 storage_free=60, events=None, world=None, materials=None,
                 stations=None, distances=None, facts=None):
        self.graph = graph
        self.stock = dict(stock or {})
        self.kits = {k: dict(v) for k, v in (kits or {}).items()}
        self.buildings = dict(buildings or {})
        self.stage = stage
        self.storage_free = storage_free
        self.events = set(events or ())
        self.world = set(world or ())
        self.materials = {k: dict(v) for k, v in (materials or {}).items()}
        self.stations = dict(stations or {})
        self.distances = dict(distances or {})
        self.facts = copy.deepcopy(facts or {})

    @classmethod
    def empty(cls, graph):
        world = cls(graph)
        world.kits["spirit-1"] = {}
        world.world = {"tree:oak", "tree:jungle", "sand", "clay", "animals",
                       "flower:poppy", "flower:dandelion", "soil"}
        for building in graph.buildings.values():
            world.buildings[building.id] = PLANNED if building.stage == 1 and not building.optional else ABSENT
        return world

    @classmethod
    def settled(cls, graph):
        world = cls.empty(graph)
        for building in ("crafting_table", "furnace", "clearing", "chests", "mine"):
            world.buildings[building] = BUILT
        world.tools("spirit-1", 1)
        world.stock.update({"oak_log": 30, "oak_planks": 20, "stick": 10,
                            "cobblestone": 40, "coal": 4})
        return world

    def tools(self, spirit, tier):
        kit = {}
        if tier >= 0:
            for kind in self.graph.kind_names():
                kit[kind] = tier
        self.kits[spirit] = kit
        return self

    def with_item(self, item, count):
        self.stock[item] = count
        return self

    def event(self, name):
        self.events.add(name)
        return self

    def built(self, building, built):
        self.buildings[building] = built
        return self

    def copy(self):
        return World(self.graph, self.stock, self.kits, self.buildings, self.stage,
                     self.storage_free, self.events, self.world, self.materials,
                     self.stations, self.distances, self.facts)

    def with_facts(self, facts):
        self.facts = copy.deepcopy(facts)
        return self

    def state(self):
        return State({
            "stock": self.stock,
            "stage": self.stage,
            "kits": self.kits,
            "buildings": self.buildings,
            "materials": self.materials,
            "storageFreePercent": self.storage_free,
            "events": sorted(self.events),
            "world": sorted(self.world),
            "distances": self.distances,
            "stations": self.stations,
            "facts": self.facts,
        })

    def plan(self):
        return Planner(self.graph, self.state()).plan()

    def after(self, task):
        """Обновляет вход после задания: причина события не повторяется бесконечно."""
        task_id = task.get("id", "")
        makes = task.get("makes")
        if task_id.startswith("event:"):
            self._after_event(task_id.split(":", 2)[1])
        if makes is not None:
            self._after_way(task_id, makes)

    def _after_event(self, name):
        facts = self.facts
        if name.startswith("repair_"):
            building = (facts.get("buildings") or {}).get(name[len("repair_"):])
            if building is not None:
                building["damage"] = 0
                building["missing"] = {}
                building["extra"] = {}
                building["wrong"] = 0
        elif name.startswith("life_threat"):
            threats = facts.get("threats") or []
            if threats:
                threats.pop(0)
        elif name in ("tool_worn", "armor_worn"):
            for spirit in (facts.get("spirits") or {}).values():
                for tool in spirit.get("tools") or []:
                    tool["durability"] = tool.get("maxDurability", 0)
                for armor in spirit.get("armor") or []:
                    armor["durability"] = armor.get("maxDurability", 0)
        elif name == "gear_equip_best":
            for chest in facts.get("chests") or []:
                chest["contents"] = {}
        elif name.startswith("gear_craft_"):
            self._gear_crafted(name[len("gear_craft_"):])
        elif name.startswith("gear_kit_"):
            for spirit in (facts.get("spirits") or {}).values():
                spirit["activity"] = None
        elif name == "demolish_stray":
            strays = facts.get("strays") or []
            if strays:
                strays.pop(0)
        elif name == "backfill":
            self._terrain("pits", -1)
            self._terrain_map("fill")
        elif name == "clear_floating":
            self._terrain("hanging", -1)
            self._terrain_map("floating")
        elif name == "level_lot":
            self._terrain("heightDrop", 0, absolute=True)
            self._terrain_map("cut")
        elif name == "restore_trace":
            self._terrain("traces", -1)

    def _gear_crafted(self, kind):
        spirits = (self.facts.get("spirits") or {}).values()
        for spirit in spirits:
            if kind == "armor":
                spirit["armor"] = [{"slot": slot, "item": "minecraft:netherite_" + suffix,
                                    "durability": 100, "maxDurability": 100}
                                   for slot, suffix in (("HEAD", "helmet"), ("CHEST", "chestplate"),
                                                        ("LEGS", "leggings"), ("FEET", "boots"))]
            else:
                spirit.setdefault("tools", []).append({
                    "item": "minecraft:netherite_" + kind, "kind": kind, "tier": 4,
                    "durability": 100, "maxDurability": 100})

    def _terrain_map(self, key):
        terrain = self.facts.get("terrain")
        if terrain is not None:
            terrain[key] = {}

    def _terrain(self, key, delta, absolute=False):
        terrain = self.facts.get("terrain")
        if terrain is None:
            return
        terrain[key] = max(0, int(terrain.get(key, 0)) + delta) if not absolute else delta

    def _after_way(self, task_id, makes):
        facts = self.facts
        if makes == "store:carry:deliver":
            for spirit in (facts.get("spirits") or {}).values():
                spirit["inventory"] = {}
        elif makes == "store:carry:transfer":
            for chest in facts.get("chests") or []:
                chest["contents"] = {}
                chest["freeSlots"] = int(chest.get("totalSlots", chest.get("freeSlots", 0)))
        elif makes == "store:smelt:load":
            station = (facts.get("stations") or {}).get("furnace")
            if station is not None:
                station["busy"] = True
        elif makes == "store:smelt:take":
            station = (facts.get("stations") or {}).get("furnace")
            if station is not None:
                station["busy"] = False
                station["output"] = None
        elif makes == "store:chests":
            self.storage_free = min(100, self.storage_free + 10)
        elif makes == "store:sorted":
            for chest in facts.get("chests") or []:
                chest["contents"] = {}
        elif makes in ("dig_open", "bridge_open", "pillar_open"):
            for spirit in (facts.get("spirits") or {}).values():
                if spirit.get("trapped"):
                    spirit["trapped"] = False
                    spirit["trap"] = None
                    break
        elif makes == "rescue":
            for spirit in (facts.get("spirits") or {}).values():
                if spirit.get("stuck"):
                    spirit["stuck"] = False
                    break
        elif makes == "trail_clean":
            self._terrain("traces", -1)
        at = task_id.find("@")
        way = self.graph.way(task_id if at < 0 else task_id[:at])
        actual = ((facts.get("stageReqs") or {}).get("actual"))
        if way is not None and actual is not None and makes in actual:
            actual[makes] = int(actual.get(makes, 0)) + way.yield_


def baselines(graph):
    empty = World.empty(graph)
    settled = World.settled(graph)
    crowded = World.settled(graph).tools("spirit-2", -1).tools("spirit-3", 2)
    second = World.settled(graph)
    second.stage = 2
    for building_id in graph.buildings:
        second.buildings[building_id] = PLANNED
    ten = World.settled(graph)
    for index in range(2, 11):
        ten.tools("spirit-" + str(index), -1)
    return [empty, settled, crowded, second, ten]


MORE_SIGNALS = (
    "more:player_near", "more:player_building", "more:dark_spots", "more:dusk_soon",
    "more:bad_trip_conditions", "more:failures_repeat", "more:station_queue",
    "more:drops_expiring", "more:forest_depleted", "more:ore_depleted", "more:fence_gap",
    "more:door_open_night", "more:hopper_uncovered", "more:reserve_unused",
    "more:work_duplicate", "more:strategic_goal",
)

MORE2_EVENTS = (
    "more2_food_low", "more2_inventory_full", "more2_inventory_space", "more2_long_carry",
    "more2_fire_near_building", "more2_lava_near_building", "more2_hazard_flow",
    "more2_animal_outside", "more2_wild_herd", "more2_villager_near", "more2_villager_trade",
    "more2_stage_prep", "more2_bed_needed", "more2_path_needed",
)

MORE3_EVENTS = (
    "danger_spot", "spirit_death_drop", "work_yield_low", "trash_found",
    "chest_purpose_missing", "work_conflict", "station_idle", "player_sleep",
    "sand_collapse", "gravel_collapse", "farmland_trampled", "world_decay", "stage_slow",
)

MORE5_EVENTS = (
    "more5_player_weak", "more5_crops_dark", "more5_saplings_dark", "more5_crops_dry",
    "more5_growth_unfed", "more5_fuel_low", "more5_build_shortage", "more5_mob_broken",
    "more5_route_blocked", "more5_chest_far", "more5_mine_dark", "more5_wood_drops",
)

MORE4_EVENTS = (
    "more4_player_near", "more4_player_hungry", "more4_player_tool_broken",
    "more4_wrong_tool_hand", "more4_portal_found", "more4_portal_unsafe",
    "more4_space_found", "more4_space_tight", "more4_tamed_pet", "more4_rare_found",
    "more4_rare_mixed", "more4_caution",
)

MORE7_EVENTS = (
    "more7_work_pileup", "more7_gated_item", "more7_morning_harvest",
    "more7_evening_return", "more7_pen_full", "more7_field_full", "more7_order_ready",
)

MORE6_EVENTS = (
    "more6_spirits_short", "more6_weak_far", "more6_build_unfinished",
    "more6_carry_materials", "more6_station_broken", "more6_loose_items",
    "more6_water_needed", "more6_lava_needed", "more6_repeat_demand",
    "more6_spirit_idle",
)

AREA_STOCK = {
    "oak_log": 200, "oak_planks": 200, "stick": 100, "cobblestone": 300, "coal": 100,
    "charcoal": 50, "iron_ingot": 50, "diamond": 20, "obsidian": 10, "book": 5,
    "dirt": 200, "sand": 50, "gravel": 50, "torch": 100, "wheat": 50, "melon_seeds": 50, "pumpkin_seeds": 50,
    "white_wool": 20, "food": 50, "bucket": 5, "leather": 50, "flint": 20, "feather": 20,
    "bone": 10, "string": 10, "rotten_flesh": 10, "bricks": 50, "bed": 4,
    "melon_slice": 2, "pumpkin": 2, "cocoa_beans": 8, "blaze_rod": 2, "flint_and_steel": 1,
}


def ready_world(graph, stage=3, spirits=20):
    """Мир у конца этапа: всё построено, кроме одной постройки — есть чем закончить."""
    world = World.settled(graph)
    world.world.clear()
    world.stage = stage
    last = None
    triggered = {trigger.get("build") for trigger in graph.triggers}
    for building in graph.buildings.values():
        if building.optional:
            world.buildings[building.id] = BUILT if building.id in triggered else ABSENT
        elif building.stage > stage:
            world.buildings[building.id] = ABSENT
        elif building.stage < stage:
            world.buildings[building.id] = BUILT
        else:
            world.buildings[building.id] = BUILT
            if last is None or building.id == "kennel":
                last = building.id
    if last is not None:
        world.buildings[last] = PLANNED
    for index in range(1, spirits + 1):
        world.tools("spirit-" + str(index), 3)
    world.stock.update(AREA_STOCK)
    for item_id in graph.items:
        if item_id.startswith("herd_"):
            world.stock[item_id] = 0
        else:
            world.stock[item_id] = max(world.stock.get(item_id, 0), 100000)
    return world


def _life_world(graph):
    world = ready_world(graph)
    threats = []
    for index in range(8):
        threats.append({
            "id": "threat-" + str(index), "type": "minecraft:zombie",
            "health": 10, "maxHealth": 20,
            "position": {"x": index, "y": 64, "z": 0},
            "targetKind": "SPIRIT" if index % 2 else "PLAYER", "distance": 3,
        })
    world.events.add("life_injury")
    return world.with_facts({"threats": threats})


def _store_world(graph):
    world = ready_world(graph)
    world.storage_free = 10
    world.events.update(("store_minimum", "storage_full", "furnace_ready", "transfer_pending"))
    world.with_facts({
        "spirits": {"spirit-1": {"id": "spirit-1", "position": {"x": 0, "y": 64, "z": 0},
                                 "inventory": {"oak_log": 4}}},
        "chests": [
            {"position": {"x": 1, "y": 64, "z": 0}, "contents": {"oak_log": 10, "cobblestone": 5},
             "freeSlots": 0, "totalSlots": 27},
            {"position": {"x": 2, "y": 64, "z": 0}, "contents": {}, "freeSlots": 27,
             "totalSlots": 27},
        ],
        "stations": {"furnace": {"id": "furnace", "type": "furnace", "position": None,
                                 "busy": True, "fuel": "coal", "input": "iron_ore",
                                 "output": "iron_ingot"}},
        "stageReqs": {"minimum": {"cobblestone": 42}, "actual": {"cobblestone": 40}},
    })
    return world


def _area_world(graph):
    world = ready_world(graph)
    damaged = {name[len("repair_"):] for name in graph.events if name.startswith("repair_")}
    world.with_facts({
        "strays": [{"id": "stray-1", "type": "wall", "position": {"x": 5, "y": 64, "z": 5},
                    "builder": "spirit-1", "blocks": {"wall": 1}}],
        "terrain": {"pits": 2, "hanging": 1, "heightDrop": 4, "deepestPit": 3, "traces": 2,
                    "cut": {"minecraft:dirt": 2}, "fill": {"minecraft:dirt": 3},
                    "floating": {"minecraft:oak_log": 4}},
        "buildings": {building: {"id": building, "damage": 5, "missing": {"minecraft:dirt": 3},
                                 "extra": {}, "wrong": 0, "auditPercent": 75, "unknown": False}
                      for building in damaged if building in graph.buildings},
    })
    return world


def _gear_world(graph):
    world = ready_world(graph)
    spirits = {}
    for index, activity in enumerate(("mining", "woodcutting", "farming", "building"), start=1):
        spirits["spirit-" + str(index)] = {
            "id": "spirit-" + str(index), "position": {"x": index, "y": 64, "z": 0},
            "health": 20, "food": 20, "air": 300, "inWater": False, "trapped": False,
            "stuck": False, "activity": activity, "inventory": {}, "armor": [], "tools": [],
        }
    spirits["spirit-1"]["armor"] = [{"slot": "HEAD", "item": "minecraft:leather_helmet",
                                     "durability": 5, "maxDurability": 55}]
    spirits["spirit-1"]["tools"] = [{"item": "minecraft:stone_pickaxe", "kind": "pickaxe",
                                     "tier": 1, "durability": 5, "maxDurability": 131}]
    world.with_facts({
        "spirits": spirits,
        "chests": [{"position": {"x": 1, "y": 64, "z": 0},
                    "contents": {"minecraft:iron_pickaxe": 1, "minecraft:iron_chestplate": 1},
                    "freeSlots": 10, "totalSlots": 27}],
    })
    return world


def _more_world(graph):
    world = ready_world(graph)
    world.world.update(MORE_SIGNALS)
    for item in graph.items.values():
        if item.id.startswith("more:"):
            threshold = int(math.ceil(item.xmin * len(world.kits) * world.stage * item.k))
            world.stock[item.id] = max(0, threshold - 1)
    return world


FARM_EVENTS = (
    "crop_ready", "crop_dry", "animal_starving", "pen_full", "orchard_empty",
    "field_empty", "storage_mixed", "furnace_needs_fuel",
)


def _farm_world(graph):
    world = ready_world(graph)
    world.events.update(FARM_EVENTS)
    world.stock["herd_cow"] = 119
    world.stock["farmland"] = 0
    world.stock["food_reserve"] = 0
    world.stock["milk_bucket"] = 0
    world.stock["white_wool"] = 239
    world.world.add("animals")
    return world


def _more2_world(graph):
    world = ready_world(graph)
    world.events.update(MORE2_EVENTS)
    return world


def _more3_world(graph):
    world = ready_world(graph)
    world.events.update(MORE3_EVENTS)
    return world


def _more5_world(graph):
    world = ready_world(graph)
    world.events.update(MORE5_EVENTS)
    return world


def _more4_world(graph):
    world = ready_world(graph)
    world.events.update(MORE4_EVENTS)
    return world


def _more7_world(graph):
    world = ready_world(graph)
    world.events.update(MORE7_EVENTS)
    return world


def _more6_world(graph):
    world = ready_world(graph)
    world.events.update(MORE6_EVENTS)
    return world


def _dirty_world(graph):
    world = ready_world(graph)
    damaged = [bid for bid, building in graph.buildings.items()
               if not building.optional and building.stage <= 3 and world.buildings.get(bid) == BUILT]
    damaged = damaged[:4]
    world.with_facts({
        "strays": [{"id": "stray-" + str(index), "type": "wall",
                    "position": {"x": index, "y": 64, "z": 0}, "blocks": {"wall": 1}}
                   for index in range(236)],
        "terrain": {"pits": 760, "hanging": 120, "heightDrop": 50, "deepestPit": 5,
                    "traces": 30, "cut": {"minecraft:dirt": 100},
                    "fill": {"minecraft:dirt": 200}, "floating": {"minecraft:oak_log": 50}},
        "buildings": {bid: {"id": bid, "damage": 5, "missing": {"minecraft:dirt": 3},
                            "extra": {}, "wrong": 0, "auditPercent": 60, "unknown": False}
                      for bid in damaged},
        "feedback": {"clock": {"morning": False, "duskSoon": True, "night": False, "dayTime": 12000},
                     "player": {"near": True, "holdingBlock": True, "usingItem": False, "digging": False},
                     "failures": []},
    })
    return world


def _blocked_world(graph):
    world = ready_world(graph)
    world.world.update({"more:bad_trip_conditions", "more:work_duplicate"})
    world.distances.update({"tree:oak": 240, "tree:jungle": 240, "animals": 180})
    world.events.add("more7_work_pileup")
    world.with_facts({
        "spirits": {"spirit-1": {"id": "spirit-1", "position": {"x": 0, "y": 64, "z": 0},
                                 "stuck": True, "trapped": False}},
        "feedback": {
            "clock": {"morning": True, "duskSoon": True, "night": False, "dayTime": 12000},
            "player": {"near": True, "holdingBlock": False, "usingItem": False, "digging": True},
            "failures": [
                {"node": "mine_cobble", "target": "cobblestone", "count": 3, "cause": "NO_PATH", "lastTick": 10},
                {"node": "chop_oak_log", "target": "oak_log", "count": 2, "cause": "NO_PATH", "lastTick": 20},
                {"node": "mine_iron", "target": "iron_ingot", "count": 3, "cause": "NOT_REACHED", "lastTick": 30},
            ],
        },
    })
    return world


def _field_world(graph):
    world = ready_world(graph)
    world.buildings["wheat_field"] = PLANNED
    world.stock["wheat_seeds"] = 64
    world.stock["dirt"] = 64
    world.events.add("field_empty")
    return world


def _dig_world(graph):
    world = ready_world(graph)
    world.stock["trail_clean"] = 0
    world.with_facts({"spirits": {
        "spirit-1": {"id": "spirit-1", "position": {"x": 0, "y": 64, "z": 0},
                     "trapped": True, "trap": "sealed"},
        "spirit-2": {"id": "spirit-2", "position": {"x": 1, "y": 64, "z": 0},
                     "trapped": True, "trap": "pit"},
        "spirit-3": {"id": "spirit-3", "position": {"x": 2, "y": 64, "z": 0},
                     "trapped": True, "trap": "water"},
    }})
    return world


def input_builders():
    """Сборщики миров областей: (имя, graph -> World)."""
    return [
        ("life", _life_world),
        ("store", _store_world),
        ("farm", _farm_world),
        ("world", _area_world),
        ("gear", _gear_world),
        ("more", _more_world),
        ("more2", _more2_world),
        ("more3", _more3_world),
        ("more4", _more4_world),
        ("more5", _more5_world),
        ("dirty", _dirty_world),
        ("blocked", _blocked_world),
        ("field", _field_world),
        ("more7", _more7_world),
        ("more6", _more6_world),
        ("dig", _dig_world),
    ]


def input_worlds(graph):
    """Стартовые миры областей: каждое новое событие и вход участвуют в подборе весов."""
    return [(name, builder(graph)) for name, builder in input_builders()]


def unbuilt(world):
    return [b.id for b in world.graph.buildings.values()
            if b.stage <= world.stage and not b.optional
            and world.buildings.get(b.id) != BUILT]


def promote(world):
    while world.stage <= LAST_STAGE and not unbuilt(world):
        world.stage += 1
        stage = world.stage
        for building in world.graph.buildings.values():
            if building.stage == stage and not building.optional \
                    and world.buildings.get(building.id) != BUILT:
                world.buildings[building.id] = PLANNED


def issue_order(plan):
    tasks = [task for task in plan["tasks"] if task["feasible"]]
    tasks.sort(key=lambda task: (-task["priority"], task["id"]))
    return tasks


def have(world, item):
    if world.graph.is_group(item):
        return sum(max(0, world.stock.get(member, 0)) for member in world.graph.members(item))
    return world.stock.get(item, 0)


def stocked(world, task):
    return all(have(world, item) >= value for item, value in task["consumes"].items())


def consume(world, item, count):
    if not world.graph.is_group(item):
        world.stock[item] = world.stock.get(item, 0) - count
        return
    left = count
    for member in world.graph.members(item):
        take = min(left, max(0, world.stock.get(member, 0)))
        world.stock[member] = world.stock.get(member, 0) - take
        left -= take


def answer(world, plan, strategist=None):
    for notice in plan["notices"]:
        building_id = notice.get("building")
        wanted = world.graph.buildings.get(building_id)
        if wanted is not None and wanted.stage <= world.stage and world.buildings.get(wanted.id) != BUILT:
            world.buildings[wanted.id] = PLANNED
            if strategist is not None:
                strategist.append(str(world.stage) + ":" + wanted.id)


def finish(world, work, wear):
    """GraphSimulation.finish: результат задания в момент его завершения."""
    task = work["task"]
    task_id = task["id"]
    world.after(task)
    if task_id.startswith("explore:"):
        world.world.add(task["makes"])
        return
    if task_id.startswith("station:"):
        world.stations[task["makes"]] = world.stations.get(task["makes"], 1) + 1
        return
    if task_id.startswith("event:"):
        world.events.discard(task_id.split(":", 2)[1])
        return
    if task_id.startswith("building:"):
        world.buildings[task["makes"]] = BUILT
        if task["makes"] == world.graph.storage_building:
            world.storage_free = max(world.storage_free, 60)
        return
    costs = world.graph.costs
    if world.graph.is_tool(task["makes"]):
        split = task["makes"].find("_")
        kind = task["makes"][split + 1:]
        tier = task["makes"][:split]
        owner = task["spirit"] or work["spirit"]
        world.kits[owner][kind] = max(world.kits[owner].get(kind, -1), world.graph.rank(tier))
        wear[owner + ":" + kind] = costs.durability(tier)
        return
    at = task_id.find("@")
    way = world.graph.way(task_id if at < 0 else task_id[:at])
    if way is None:
        return
    world.stock[task["makes"]] = world.stock.get(task["makes"], 0) + way.yield_
    if way.tool is not None:
        spirit = work["spirit"]
        key = spirit + ":" + way.tool
        rank = world.kits.get(spirit, {}).get(way.tool, -1)
        fresh = MAX_INT if rank < 0 else costs.durability(world.graph.tiers[rank])
        left = wear.get(key, fresh) - costs.uses(way.id, way.yield_)
        if left <= 0 and spirit in world.kits:
            world.kits[spirit].pop(way.tool, None)
            wear.pop(key, None)
        else:
            wear[key] = left


def assign(world, plan, now, free_at, running, seq, log):
    """GraphSimulation.assign: один заказ на свободного духа, время — тиками задания."""
    started = False
    taken = {work["task"]["id"] for _at, _seq, work in running}
    trail, stage_steps, streak = log
    for spirit in list(world.kits):
        if free_at.get(spirit, now) > now:
            continue
        chosen = None
        for task in issue_order(plan):
            if task["id"] in taken:
                continue
            if task["spirit"] is not None and task["spirit"] != spirit:
                continue
            if not stocked(world, task):
                continue
            chosen = task
            break
        if chosen is None:
            free_at[spirit] = now + IDLE_TICKS
            continue
        taken.add(chosen["id"])
        for item, count in chosen["consumes"].items():
            consume(world, item, count)
        finish_at = now + max(1, chosen["ticks"])
        work = {"finish_at": finish_at, "spirit": spirit, "task": chosen}
        heapq.heappush(running, (finish_at, seq[0], work))
        seq[0] += 1
        free_at[spirit] = finish_at
        trail.append((world.stage, chosen["id"], chosen["priority"]))
        stage_steps[world.stage] = stage_steps.get(world.stage, 0) + 1
        streak[chosen["id"]] = streak.get(chosen["id"], 0) + 1
        started = True
    return started


def _solve_flows(flows, device, direct=True):
    """Батчевая матричная система (I − A)·p = r на torch; без torch — по одному."""
    if torch is None or not flows:
        return [flow.solve() for flow in flows]
    if device == "cuda" and not torch.cuda.is_available():
        device = "cpu"
    if direct:
        return _solve_direct(flows, device)
    size = max(len(flow.index) for flow in flows)
    edges = max(1, max(len(flow.links) for flow in flows))
    count = len(flows)
    base = torch.zeros((count, size), dtype=torch.float64, device=device)
    source = torch.zeros((count, edges), dtype=torch.long, device=device)
    target = torch.zeros((count, edges), dtype=torch.long, device=device)
    factor = torch.zeros((count, edges), dtype=torch.float64, device=device)
    for row, flow in enumerate(flows):
        for key, value in flow.roots.items():
            base[row, flow.index[key]] += value
        for column, (a, b, f) in enumerate(flow.links):
            source[row, column] = flow.index[a]
            target[row, column] = flow.index[b]
            factor[row, column] = f
    value = base.clone()
    active = torch.ones(count, dtype=torch.bool, device=device)
    for _ in range(MAX_ITERATIONS):
        contribution = value.gather(1, source) * factor
        nxt = base.clone()
        nxt.scatter_add_(1, target, contribution)
        delta = (nxt - value).abs().max(dim=1).values
        value = torch.where(active.unsqueeze(1), nxt, value)
        active = active & (delta >= EPSILON)
        if not bool(active.any()):
            break
    result = []
    for row, flow in enumerate(flows):
        row_values = value[row]
        result.append({key: float(row_values[idx]) + flow.locals.get(key, 0.0)
                       for key, idx in flow.index.items()})
    return result


def _solve_direct(flows, device):
    """(I - A)*p = r одним матричным решением на видеокарте.

    Матрица и правые части собираются плоским scatter_add_: без питоновских
    присваиваний в тензор и без лишних синхронизаций с видеокартой.
    """
    size = max(len(flow.index) for flow in flows)
    count = len(flows)
    base_rows = []
    base_cols = []
    base_vals = []
    rows = []
    sources = []
    targets = []
    factors = []
    for row, flow in enumerate(flows):
        for key, value in flow.roots.items():
            base_rows.append(row)
            base_cols.append(flow.index[key])
            base_vals.append(value)
        for a, b, f in flow.links:
            rows.append(row)
            sources.append(flow.index[a])
            targets.append(flow.index[b])
            factors.append(-f)
    flat_base = torch.zeros(count * size, dtype=torch.float64, device=device)
    if base_rows:
        flat_base.scatter_add_(
            0,
            torch.tensor(base_rows, dtype=torch.long, device=device) * size
            + torch.tensor(base_cols, dtype=torch.long, device=device),
            torch.tensor(base_vals, dtype=torch.float64, device=device))
    base = flat_base.view(count, size)
    flat_matrix = torch.zeros(count * size * size, dtype=torch.float64, device=device)
    if rows:
        flat_matrix.scatter_add_(
            0,
            torch.tensor(rows, dtype=torch.long, device=device) * (size * size)
            + torch.tensor(targets, dtype=torch.long, device=device) * size
            + torch.tensor(sources, dtype=torch.long, device=device),
            torch.tensor(factors, dtype=torch.float64, device=device))
    matrix = flat_matrix.view(count, size, size)
    matrix += torch.eye(size, dtype=torch.float64, device=device)
    value = base.clone()
    active = torch.ones(count, dtype=torch.bool, device=device)
    for _ in range(MAX_ITERATIONS):
        moved = base + value - torch.bmm(matrix, value.unsqueeze(-1)).squeeze(-1)
        delta = (moved - value).abs().amax(dim=1)
        value = torch.where(active.unsqueeze(1), moved, value)
        active = active & (delta >= EPSILON)
        if not bool(active.any()):
            break
    solved = value.cpu().tolist()
    return [{key: solved[row][idx] + flow.locals.get(key, 0.0)
             for key, idx in flow.index.items()} for row, flow in enumerate(flows)]

def run_many(worlds, limit=4000, device="cuda", direct=True):
    """Симуляция пакета миров во времени (тики заданий, дорога, износ, станции)."""
    count = len(worlds)
    states = []
    for world in worlds:
        states.append({
            "world": world.copy(),
            "free_at": {},
            "wear": {},
            "running": [],
            "seq": [0],
            "now": 0,
            "stage_start": 0,
            "idle": 0,
            "trail": [],
            "stage_steps": {},
            "stage_ticks": {},
            "streak": {},
            "strategist": [],
            "finished": False,
            "stuck": "",
            "ticks": 0,
            "stage": world.stage,
        })
    alive = set(range(count))
    for _step in range(limit):
        if not alive:
            break
        for index in list(alive):
            state = states[index]
            world = state["world"]
            now = state["now"]
            for spirit in world.kits:
                state["free_at"].setdefault(spirit, now)
            while state["running"] and state["running"][0][0] <= now:
                _at, _seq, work = heapq.heappop(state["running"])
                finish(world, work, state["wear"])
            stage_before = world.stage
            promote(world)
            if world.stage != stage_before:
                state["stage_ticks"][stage_before] = now - state["stage_start"]
                state["stage_start"] = now
            if world.stage > LAST_STAGE:
                state["finished"] = True
                state["ticks"] = now
                state["stage"] = LAST_STAGE
                alive.discard(index)
        if not alive:
            break
        indexes = sorted(alive)
        planners = []
        for index in indexes:
            planner = Planner(states[index]["world"].graph, states[index]["world"].state())
            planner.prepare()
            planners.append(planner)
        scores = _solve_flows([planner.flow for planner in planners], device, direct)
        for planner, score in zip(planners, scores):
            planner.apply_edges(score)
        scores = _solve_flows([planner.flow for planner in planners], device, direct)
        for index, planner, score in zip(indexes, planners, scores):
            state = states[index]
            world = state["world"]
            now = state["now"]
            plan = planner.result(score)
            answer(world, plan, state["strategist"])
            log = (state["trail"], state["stage_steps"], state["streak"])
            started = assign(world, plan, now, state["free_at"], state["running"],
                             state["seq"], log)
            state["idle"] = 0 if (started or state["running"]) else state["idle"] + 1
            if state["idle"] > IDLE_LIMIT:
                state["stuck"] = "нет выполнимых заданий: " + str(plan["tasks"])
                alive.discard(index)
                continue
            next_free = min(state["free_at"].values()) if state["free_at"] else now + IDLE_TICKS
            next_done = state["running"][0][0] if state["running"] else MAX_INT
            state["now"] = max(now + 1, min(next_free, next_done))

    outcomes = []
    for state in states:
        world = state["world"]
        stuck = state["stuck"]
        if not state["finished"] and not stuck:
            stuck = "за " + str(limit) + " решений не дошли до конца этапа " + str(world.stage)
        outcomes.append({
            "steps": len(state["trail"]),
            "ticks": state["now"] if not state["finished"] else state["ticks"],
            "finished": state["finished"],
            "stuck": stuck,
            "trail": state["trail"],
            "stage_steps": state["stage_steps"],
            "stage_ticks": state["stage_ticks"],
            "stage": state["stage"] if state["finished"] else world.stage,
            "strategist": state["strategist"],
        })
    return outcomes


def run(world, limit=4000, device="cuda"):
    return run_many([world], limit, device)[0]


def load_graph(export):
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    base = os.path.join(root, "src", "main", "resources", "data", "theminespirit", "board")
    recipes = os.path.join(base, "graph-recipes.json")
    fragments = os.path.join(base, "graph.d")
    return Graph.load_merged(os.path.join(export, "graph.json"), recipes, fragments)


def main(argv=None):
    parser = argparse.ArgumentParser(description="Пакетная симуляция графа поселения на torch")
    parser.add_argument("--export", default=os.path.join(
        os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
        "TestResults", "graph-export"))
    parser.add_argument("--limit", type=int, default=4000)
    parser.add_argument("--device", default="cuda")
    args = parser.parse_args(argv)
    graph = load_graph(args.export)
    worlds = baselines(graph)
    outcomes = run_many(worlds, args.limit, args.device)
    print("torch:", None if torch is None else torch.__version__,
          "cuda:", bool(torch is not None and torch.cuda.is_available()))
    for index, outcome in enumerate(outcomes):
        print(f"старт {index}: шагов {outcome['steps']}, тиков {outcome['ticks']}, "
              f"этап {outcome['stage']}, "
              f"{'готово' if outcome['finished'] else 'ЗАСТРЯЛ: ' + outcome['stuck']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
