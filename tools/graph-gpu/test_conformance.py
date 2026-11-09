"""Сверка Python-модели с Java на выгрузке conformance.json.

Запуск: python test_conformance.py
Выгрузка: ./gradlew graphExport -> TestResults/graph-export/{graph,conformance}.json

Критерий: набор заданий и feasible совпадают точно, priority расходится не больше чем на 1.
"""

import copy
import json
import os
import sys
import tempfile
from collections import Counter

import search
import sim_gpu
from graph_model import ABSENT, BUILT, PLANNED, Graph, Planner, State, plan

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
EXPORT = os.path.join(ROOT, "TestResults", "graph-export")
RECIPES = os.path.join(ROOT, "mod", "src", "main", "resources", "data", "theminespirit", "board",
                       "graph-recipes.json")
FRAGMENTS = os.path.join(ROOT, "mod", "src", "main", "resources", "data", "theminespirit", "board",
                         "graph.d")


def compare_case(graph, case, index):
    expected = {task["id"]: task for task in case["tasks"]}
    actual = {task["id"]: task for task in plan(graph, State(case["state"]))["tasks"]}
    problems = []
    missing = sorted(set(expected) - set(actual))
    extra = sorted(set(actual) - set(expected))
    if missing:
        problems.append("нет заданий: " + ", ".join(missing))
    if extra:
        problems.append("лишние задания: " + ", ".join(extra))
    for task_id in sorted(set(expected) & set(actual)):
        want = expected[task_id]
        got = actual[task_id]
        if bool(want["feasible"]) != bool(got["feasible"]):
            problems.append(f"{task_id}: feasible {want['feasible']} != {got['feasible']}")
        if abs(int(want["priority"]) - int(got["priority"])) > 1:
            problems.append(f"{task_id}: priority {want['priority']} != {got['priority']}")
        if bool(want["chance"]) != bool(got["chance"]):
            problems.append(f"{task_id}: chance {want['chance']} != {got['chance']}")
    if problems:
        return f"случай {index} ({case.get('mutations')}):\n  " + "\n  ".join(problems)
    return None


def test_fragments():
    with tempfile.TemporaryDirectory() as directory:
        manual = os.path.join(directory, "graph.json")
        recipes = os.path.join(directory, "recipes.json")
        fragments = os.path.join(directory, "graph.d")
        os.makedirs(fragments)
        with open(manual, "w", encoding="utf-8") as handle:
            json.dump({"items": {"stick": {"xmin": 0, "base": 1}}, "ways": []}, handle)
        with open(recipes, "w", encoding="utf-8") as handle:
            json.dump({"items": {}, "ways": []}, handle)
        with open(os.path.join(fragments, "rope.json"), "w", encoding="utf-8") as handle:
            json.dump({
                "items": {"rope": {"xmin": 0, "base": 1}},
                "edges": [
                    {"lack": "a", "way": "b", "weight": 1},
                    {"lack": "a", "way": "c", "weight": 1},
                ],
                "triggers": [{"have": "a", "build": "b"}, {"have": "a", "build": "c"}],
            }, handle)
        graph = Graph.load_merged(manual, recipes, fragments)
        assert "rope" in graph.items, "Python-модель обязана читать фрагменты графа"
        assert len(graph.edges) == 2 and len(graph.triggers) == 2,             "Списки без id (edges, triggers) добавляются целиком, как в Java"
        with open(os.path.join(fragments, "conflict.json"), "w", encoding="utf-8") as handle:
            json.dump({"items": {"stick": {"xmin": 0, "base": 1}}}, handle)
        try:
            Graph.load_merged(manual, recipes, fragments)
        except ValueError as error:
            assert "conflict.json" in str(error), "Ошибка конфликта обязана назвать файл: " + str(error)
        else:
            raise AssertionError("Повтор id во фрагменте обязан отклоняться")
    print("OK: фрагменты графа сливаются, конфликт назван путём")


def life_facts(health=20, hurt=None, targets=()):
    spirit = {"id": "spirit-1", "health": health, "maxHealth": 20}
    if hurt is not None:
        spirit["recentHurt"] = hurt
    threats = [
        {"id": "threat-" + str(index), "type": "minecraft:zombie", "health": 10,
         "maxHealth": 20, "position": {"x": 0, "y": 64, "z": 0},
         "target": target, "targetKind": "SPIRIT", "distance": distance}
        for index, (target, distance) in enumerate(targets)
    ]
    return {"spirits": {"spirit-1": spirit}, "threats": threats}


def test_life_priority():
    graph = Graph({
        "maxPriority": 100,
        "items": {},
        "buildings": {},
        "ways": [],
        "events": {
            "life_threat": {
                "life": True,
                "tasks": [
                    {"id": "defend", "case": "ACTIVITY_DEFEND", "base": 120, "consumes": {}},
                    {"id": "sleep", "case": "ACTIVITY_REST", "base": 101, "consumes": {}},
                ],
            }
        },
    })
    hurt = {"amount": 8, "source": "minecraft:zombie", "ticksAgo": 5}
    state = State({
        "stage": 1,
        "kits": {"spirit-1": {}},
        "events": ["life_threat"],
        "facts": life_facts(health=4, hurt=hurt, targets=[("spirit-1", 2), ("spirit-1", 3)]),
    })
    by_id = {task["id"]: task["priority"] for task in plan(graph, state)["tasks"]}
    defend = by_id.get("event:life_threat:defend")
    sleep = by_id.get("event:life_threat:sleep")
    assert defend == 100, "Раненый дух и два моба дают жизни потолок 100 в Python: " + str(by_id)
    assert sleep is not None and sleep < defend, "Защита важнее сна и в Python: " + str(by_id)
    calm = State({
        "stage": 1,
        "kits": {"spirit-1": {}},
        "events": ["life_threat"],
        "facts": life_facts(),
    })
    assert not any(task["id"].startswith("event:life_threat:") and task["priority"] > 0
                   for task in plan(graph, calm)["tasks"]), \
        "Здоровый дух без угроз не поднимает жизнь в Python"
    print("OK: приоритет жизни считается из фактов и не выходит за 100 в Python")


def test_life_player_fact():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    assert graph.costs.life["pressureBase"] == 0.4 and graph.costs.life["injuryWound"] == 0.8,         "Коэффициенты формулы жизни читаются из graph.json, а не зашиты в Python: " + str(graph.costs.life)
    state = State({
        "stage": 1,
        "kits": {"spirit-1": {}},
        "events": ["life_threat_player"],
        "facts": {
            "spirits": {},
            "threats": [],
            "players": [{"name": "Steve", "position": {"x": 0, "y": 64, "z": 0},
                         "health": 4, "maxHealth": 20, "food": 20}],
        },
    })
    tasks = {task["id"]: task for task in plan(graph, state)["tasks"]}
    defend = tasks.get("event:life_threat_player:life_protect_player")
    assert defend is not None and 0 < defend["priority"] <= 100,         "Раненый игрок даёт задание жизни protect_player в пределах потолка: " + str(defend)
    healthy = State({
        "stage": 1,
        "kits": {"spirit-1": {}},
        "events": [],
        "facts": {
            "spirits": {},
            "threats": [],
            "players": [{"name": "Steve", "position": {"x": 0, "y": 64, "z": 0},
                         "health": 20, "maxHealth": 20, "food": 20}],
        },
    })
    assert not any(task["id"].startswith("event:life_threat_player:")
                   for task in plan(graph, healthy)["tasks"]),         "Здоровый игрок без угроз не поднимает жизнь в Python"
    print("OK: факт здоровья игрока и коэффициенты жизни читаются из данных графа")


def test_life_coefficient_vector():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    saved = graph.costs.life["weakHealth"]
    graph.costs.life["weakHealth"] = 0.5
    try:
        vector = (("раненый 0.45", 9, True), ("целый 0.55", 11, False))
        for label, health, expected in vector:
            state = State({
                "stage": 1,
                "kits": {"spirit-1": {}},
                "events": [],
                "facts": {
                    "spirits": {},
                    "threats": [],
                    "players": [{"name": "Steve", "position": {"x": 0, "y": 64, "z": 0},
                                 "health": health, "maxHealth": 20, "food": 20}],
                },
            })
            raised = any(task["id"].startswith("event:life_threat_player:")
                         for task in plan(graph, state)["tasks"])
            assert raised == expected, \
                "Вектор фактов жизни " + label + ": порог из graph.json даёт " + str(expected) + ", а не " + str(raised)
    finally:
        graph.costs.life["weakHealth"] = saved
    print("OK: события жизни выводятся из порога graph.json, а не из константы Python")



def test_food_need_band():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    with open(os.path.join(EXPORT, "conformance.json"), "r", encoding="utf-8") as handle:
        cases = json.load(handle)
    case = next((item for item in cases if "food_stage_two" in item.get("mutations", ())), None)
    assert case is not None, "В conformance есть случай stage 2 с пустой едой и углём"
    tasks = {task["id"]: task for task in plan(graph, State(case["state"]))["tasks"]}
    mine = tasks["mine_coal"]
    harvest = tasks.get("event:hungry:harvest_crop")
    assert harvest is not None and harvest["feasible"], \
        "Поле построено — сбор урожая подключён к голоду: " + str(harvest)
    food = ("event:hungry:hunt", "event:hungry:fish", "event:hungry:harvest_crop",
            "event:hungry:wait_food")
    food_priorities = [tasks[task]["priority"] for task in food]
    assert food_priorities == sorted(food_priorities, reverse=True), \
        "Пути еды стоят по силе в полосе потребности: " + str(food_priorities)
    assert min(food_priorities) >= graph.work_priority - 9, \
        "Вся еда не ниже полосы потребности: " + str(food_priorities)
    assert tasks["event:hungry:hunt"]["priority"] == graph.need_priority, \
        "Голод с пустой едой идёт в полосе потребности: " + str(tasks["event:hungry:hunt"])
    assert mine["priority"] <= graph.work_priority, \
        "Шахта остаётся обычной работой под полосой потребности: " + str(mine["priority"])
    fed = State({
        "stage": 2,
        "stock": {"food": 8, "coal": 0},
        "kits": {"spirit-1": {}},
        "buildings": {"wheat_field": "BUILT", "mine": "BUILT"},
        "events": ["hungry"],
        "world": [],
    })
    fed_tasks = {task["id"]: task for task in plan(graph, fed)["tasks"]}
    eat = fed_tasks["event:hungry:eat"]["priority"]
    assert eat >= graph.work_priority - 9, \
        "Обед голодного духа с едой в запасе — тоже потребность: " + str(fed_tasks.get("event:hungry:eat"))
    assert eat <= graph.need_priority, \
        "Обед не выходит за верх полосы потребности: " + str(eat)
    print("OK: голод на этапе 2 выше шахты, сбор урожая подключён")


def dig_inputs(graph, facts):
    state = State({
        "stock": {},
        "stage": 1,
        "kits": {"spirit-1": {}},
        "buildings": {"crafting_table": "BUILT", "chests": "BUILT", "furnace": "BUILT"},
        "storageFreePercent": 60,
        "world": [],
        "facts": facts,
    })
    return [task["id"] for task in plan(graph, state)["tasks"]]


def test_dig_inputs():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    tasks = dig_inputs(graph, {"spirits": {"spirit-1": {"trapped": True, "trap": "sealed"}}})
    assert "dig_out" in tasks and "restore_trace" in tasks,         "дух без пути обязан дать dig_out и restore_trace"
    stuck = dig_inputs(graph, {"spirits": {"spirit-1": {"trapped": False, "stuck": True}}})
    assert "rescue_spirit" in stuck and "restore_trace" in stuck,         "застрявший без замуровки дух обязан дать rescue_spirit и restore_trace: " + str(stuck)
    print("OK: вход копания даёт узлы dig_out и restore_trace")


def test_area_nodes():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    for way in ("transfer_item", "sort_chest", "restock_food", "till_soil", "plant_crop",
                "harvest_crop", "feed_animals", "lure_animal", "milk_animal"):
        assert graph.way(way) is not None, "Python-модель не видит новый путь области: " + way
    for building in ("storage_space", "wheat_field"):
        assert building in graph.buildings, "Python-модель не видит новую постройку: " + building
    for event in ("store_minimum", "storage_full", "furnace_ready", "crop_ready",
                  "animal_starving", "orchard_empty"):
        assert event in graph.events, "Python-модель не видит новое событие входа: " + event
    print("OK: узлы и события областей store/farm читаются из фрагментов")


def test_store_nodes():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    state = {
        "stock": {},
        "stage": 1,
        "kits": {"spirit-1": {}},
        "buildings": {"crafting_table": "BUILT", "furnace": "BUILT", "chests": "BUILT", "mine": "BUILT"},
        "materials": {},
        "storageFreePercent": 60,
        "events": [],
        "world": ["tree:oak"],
        "distances": {},
        "stations": {"furnace": 1},
        "orders": {},
        "facts": {
            "spirits": {},
            "threats": [],
            "chests": [],
            "resources": [],
            "stations": {
                "furnace": {"id": "furnace", "type": "furnace", "position": None,
                            "busy": False, "fuel": "coal", "input": None, "output": None}
            },
            "buildings": {},
            "strays": [],
            "terrain": {"pits": 0, "hanging": 0, "heightDrop": 0},
            "stageReqs": {"minimum": {"cobblestone": 160}, "actual": {"cobblestone": 100}},
        },
    }
    tasks = {task["id"] for task in plan(graph, State(state))["tasks"]}
    assert "mine_cobble" in tasks, "Python: недобор минимума склада обязан дать добычу булыжника"
    assert "store_smelt_load" in tasks, "Python: свободная печь — путь загрузки"
    print("OK: узлы склада учитывают минимум и свободную печь")


def test_world_fragment():
    graph = Graph.load_merged(
        os.path.join(ROOT, "mod", "src", "main", "resources", "data", "theminespirit", "board", "graph.json"),
        RECIPES,
        FRAGMENTS)
    for node in ("demolish_stray", "backfill", "clear_floating", "level_lot", "restore_trace"):
        assert node in graph.events, "Python-модель обязана читать узел мира '" + node + "' из фрагмента"
        task = graph.events[node]["tasks"][0]
        assert task["id"] == node, "Задание узла мира называет узел: " + node
        assert 40 <= int(task["base"]) <= 100, "Стартовый вес узла мира разумен: " + node
    assert "repair_hut" in graph.events, "Python-модель обязана читать узел ремонта из фрагмента"
    assert graph.events["repair_hut"]["tasks"][0]["id"] == "repair:hut", "Ремонт называет постройку"
    assert "#building_blocks" in graph.groups, "Группа возврата материалов объявлена фрагментом"
    print("OK: узлы мира читаются из фрагмента")


def test_world_inputs():
    state = State({
        "stage": 1,
        "kits": {"spirit-1": {}},
        "facts": {
            "strays": [{"id": "s", "type": "wall", "position": {"x": 1, "y": 64, "z": 1},
                        "builder": "spirit-1", "blocks": {"wall": 1}}],
            "terrain": {"pits": 1, "hanging": 1, "heightDrop": 2, "deepestPit": 3, "traces": 1,
                        "cut": {"minecraft:dirt": 2}, "fill": {"minecraft:dirt": 3},
                        "floating": {"minecraft:oak_log": 4}},
            "buildings": {"hut": {"id": "hut", "damage": 5, "missing": {"minecraft:dirt": 3},
                                  "extra": {}, "wrong": 0, "auditPercent": 75, "unknown": False}},
        },
    })
    for node in ("demolish_stray", "backfill", "clear_floating", "level_lot", "restore_trace", "repair_hut"):
        assert node in state.events, "Вход участка добавляет узел '" + node + "' в выбор"
    unknown = State({"stage": 1, "kits": {"spirit-1": {}}, "facts": {
        "buildings": {"hut": {"id": "hut", "damage": 5, "unknown": True}}}})
    assert "repair_hut" not in unknown.events, "Неизвестный аудит не ставит ремонт"
    natural = State({"stage": 1, "kits": {"spirit-1": {}}, "facts": {
        "terrain": {"pits": 0, "hanging": 0, "heightDrop": 0, "traces": 0,
                    "cut": {}, "fill": {}, "natural": 3263}}})
    assert "level_lot" not in natural.events, "Природный склон вне лотов не ставит level_lot в Python"
    assert "backfill" not in natural.events, "Природная впадина вне лотов не ставит backfill в Python"
    print("OK: входы участка учитываются в выборе")


def test_gear_fragment():
    graph = Graph.load_merged(
        os.path.join(ROOT, "mod", "src", "main", "resources", "data", "theminespirit", "board", "graph.json"),
        RECIPES,
        FRAGMENTS)
    for node in ("gear_equip_best", "gear_craft_armor", "gear_craft_pickaxe", "gear_craft_axe",
                 "gear_craft_shovel", "gear_craft_sword", "gear_craft_hoe", "gear_kit_mining"):
        assert node in graph.events, "Python-модель обязана читать узел снаряжения '" + node + "'"
    assert graph.events["gear_craft_pickaxe"]["tasks"][0]["id"] == "craft_tool:pickaxe", \
        "Задание кирки называет вид инструмента"
    print("OK: узлы снаряжения читаются из фрагмента")


def test_gear_inputs():
    state = State({
        "stage": 1,
        "kits": {"spirit-1": {}},
        "facts": {
            "spirits": {"spirit-1": {
                "id": "spirit-1", "position": {"x": 0, "y": 64, "z": 0},
                "activity": "mining", "inventory": {},
                "armor": [], "tools": []}},
            "chests": [],
        },
    })
    for node in ("gear_craft_pickaxe", "gear_craft_axe", "gear_kit_mining"):
        assert node in state.events, "Снаряжение без кирки обязано добавить узел '" + node + "'"
    worn = State({
        "stage": 2,
        "kits": {"spirit-1": {}},
        "facts": {
            "spirits": {"spirit-1": {
                "id": "spirit-1", "position": {"x": 0, "y": 64, "z": 0},
                "inventory": {}, "armor": [],
                "tools": [{"item": "minecraft:iron_pickaxe", "kind": "pickaxe", "tier": 2,
                           "durability": 10, "maxDurability": 250}]}},
            "chests": [],
        },
    })
    assert "tool_worn" in worn.events, "Изношенная кирка обязана поднять tool_worn"
    print("OK: входы снаряжения учитываются в выборе")
MORE = {
    "more:player_near": "more_help_player",
    "more:player_building": "more_keep_clear",
    "more:dark_spots": "more_light_dark_spot",
    "more:dusk_soon": "more_return_before_night",
    "more:drops_expiring": "more_pickup_drops",
    "more:forest_depleted": "more_replant",
    "more:ore_depleted": "more_sustain_mine",
    "more:fence_gap": "more_secure_fence",
    "more:door_open_night": "more_secure_door",
    "more:hopper_uncovered": "more_secure_hopper",
    "more:reserve_unused": "more_share_reserve",
}


MORE_READY = {
    "more_help_player", "more_keep_clear", "more_light_dark_spot", "more_return_before_night",
    "more_pickup_drops", "more_replant", "more_sustain_mine", "more_secure_fence",
    "more_secure_door", "more_secure_hopper", "more_share_reserve",
}


def test_more_nodes():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    for signal, way in MORE.items():
        path = graph.way(way)
        assert path is not None, "Python-модель не видит путь области more: " + way
        assert path.makes in graph.items, "Python-модель не видит узел области more: " + path.makes
        assert graph.items[path.makes].base > 0, "Стартовый вес узла more положителен: " + path.makes
        assert graph.implemented(way), "Действие области more связано с исполнением: " + way
        assert path.world == signal, "Вход области more — метка мира " + signal
    for way in ("more_avoid_far_trip", "more_failure_backoff", "more_station_queue",
                "more_avoid_duplicate", "more_strategic_goal"):
        assert graph.way(way) is None, "Правило выбора живёт в механике графа, а не путём: " + way
    print("OK: узлы области more читаются из фрагментов, правила выбора не выдаются работой")


def more_state(events):
    return {
        "stock": {},
        "stage": 2,
        "kits": {"spirit-1": {}},
        "buildings": {},
        "materials": {},
        "storageFreePercent": 60,
        "events": [],
        "world": events,
        "distances": {},
        "stations": {},
        "orders": {},
        "facts": {},
    }


def test_more_inputs():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    tasks = {task["id"] for task in plan(graph, State(more_state(list(MORE))))["tasks"]}
    for way in MORE.values():
        assert way in tasks, "Python: вход области more обязан выбрать путь " + way
    quiet = {task["id"] for task in plan(graph, State(more_state([])))["tasks"]}
    for way in MORE.values():
        assert way not in quiet, "Python: без входа области more задачи нет: " + way
    assert not any(task.startswith("explore:more:") for task in quiet), \
        "Python: условие more не «нет источника» и не просит разведку: " + str(sorted(quiet))
    facts = State({"stage": 1, "kits": {"spirit-1": {}}, "facts": {"feedback": {
        "clock": {"morning": False, "duskSoon": True, "night": False, "dayTime": 12000},
        "player": {"position": {"x": 1, "y": 64, "z": 2}, "near": True,
                   "holdingBlock": False, "usingItem": False, "digging": True}}}})
    assert facts.clock["duskSoon"] is True, "Python-модель обязана читать факт dusk_soon"
    assert facts.player["digging"] is True, "Python-модель обязана читать факт игрока: копает рядом"
    print("OK: входы области more учитываются в выборе")


MORE2_READY = {
    "more2_sow_food", "more2_breed_food", "more2_unload_carry", "more2_put_out_fire",
    "more2_remove_lava", "more2_block_flow", "more2_pen_animal", "more2_lure_wild",
    "more2_guard_villager", "more2_prepare_stage", "more2_make_bed", "more2_build_trail",
}


def test_more2_nodes():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    for way in ("more2_sow_food", "more2_breed_food", "more2_unload_carry",
                "more2_put_out_fire", "more2_remove_lava", "more2_block_flow",
                "more2_pen_animal", "more2_lure_wild", "more2_guard_villager",
                "more2_prepare_stage", "more2_make_bed", "more2_build_trail"):
        assert graph.way(way) is not None, "Python-модель не видит новый путь области graphmore2: " + way
        if way in MORE2_READY:
            assert graph.implemented(way), "Действие области graphmore2 связано с исполнением: " + way
        else:
            assert not graph.implemented(way), "Сигнал торговли остаётся без отдельного действия: " + way
    for way in ("more2_load_carry", "more2_batch_carry"):
        assert graph.way(way) is None, "Перенос и полевой сундук уже в путях склада: " + way
    for item in ("more2:food_plan", "more2:carry_room", "more2:safe",
                 "more2:flow_blocked", "more2:animal_penned", "more2:village_safe",
                 "more2:stage_ready", "more2:bed_ready", "more2:trail"):
        assert item in graph.items, "Python-модель не видит новый узел области graphmore2: " + item
    for building in ("more2_field_chest", "more2_trail"):
        assert building in graph.buildings, "Python-модель не видит новую постройку области: " + building
        assert graph.implemented(building), "Постройка области graphmore2 связана с исполнением: " + building
    for event in ("more2_food_low", "more2_inventory_full", "more2_inventory_space", "more2_long_carry",
                  "more2_fire_near_building", "more2_lava_near_building", "more2_hazard_flow",
                  "more2_animal_outside", "more2_wild_herd", "more2_villager_near",
                  "more2_stage_prep", "more2_bed_needed", "more2_path_needed"):
        assert event in graph.events, "Python-модель не видит новое событие области: " + event
    transfer = graph.events["more2_inventory_space"]["tasks"][0]["id"]
    field = graph.events["more2_long_carry"]["tasks"][0]["id"]
    assert transfer == "store_carry_transfer", "Перенос в руках ведёт к пути склада: " + transfer
    assert field == "more5_place_site_chest", "Дальний перенос ведёт к полевому сундуку: " + field
    print("OK: узлы и события области graphmore2 читаются из фрагмента, дубли перенесены")


def test_more2_inputs():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    state = State({
        "stage": 1,
        "kits": {"spirit-1": {}},
        "events": ["more2_fire_near_building", "more2_hazard_flow"],
    })
    tasks = {task["id"]: task for task in plan(graph, state)["tasks"]}
    fire = tasks.get("event:more2_fire_near_building:more2_put_out_fire")
    flow = tasks.get("event:more2_hazard_flow:more2_block_flow")
    assert fire is not None and flow is not None, "Вход области graphmore2 обязан дать задания в выборе"
    assert fire["implemented"] and flow["implemented"], "Тушение огня и заливка потока связаны с исполнением"
    print("OK: входы области graphmore2 учитываются в выборе")




def test_more3_nodes():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    events = ("danger_spot", "spirit_death_drop", "trash_found", "chest_purpose_missing",
              "sand_collapse", "gravel_collapse", "farmland_trampled", "world_decay")
    ways = ("secure_danger", "recover_death_drop", "clear_trash",
            "assign_chest_purpose")
    for event in events:
        assert event in graph.events, "Python-модель не видит событие области more3: " + event
    for way in ways:
        assert graph.way(way) is not None, "Python-модель не видит путь области more3: " + way
    for way in ("retune_work_cost", "spread_work", "use_idle_station", "home_night_work",
                "raise_bottleneck_weight"):
        assert graph.way(way) is None, "Правило выбора области more3 живёт в механике: " + way
    assert "#trash" in graph.groups, "Python-модель не видит группу мусора"
    tasks = {task["id"] for task in plan(graph, State({
        "stage": 1, "kits": {"spirit-1": {}}, "events": list(events)}))["tasks"]}
    for event in events:
        assert any(task.startswith("event:" + event + ":") for task in tasks), \
            "Python: событие области more3 не даёт задания: " + event
    print("OK: узлы и события области more3 учитываются в выборе")


MORE6_EVENTS = (
    "more6_spirits_short", "more6_weak_far",
    "more6_carry_materials", "more6_station_broken", "more6_loose_items",
    "more6_water_needed", "more6_lava_needed", "more6_repeat_demand",
    "more6_spirit_idle",
)

MORE6_WAYS = (
    "more6_deposit_materials", "more6_refuel_station",
    "more6_place_station", "more6_pickup_items", "more6_fetch_water",
    "more6_fetch_lava", "more6_maintain_stock", "more6_assign_idle",
)
MORE6_IMPLEMENTED = {
    "more6_deposit_materials", "more6_refuel_station", "more6_place_station",
    "more6_pickup_items", "more6_fetch_water", "more6_fetch_lava",
    "more6_maintain_stock", "more6_assign_idle",
}


def test_more6_nodes():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    for event in MORE6_EVENTS:
        assert event in graph.events, "Python-модель не видит событие области more6: " + event
    for way in ("more6_call_spirit", "more6_return_weak", "more6_send_near", "more6_finish_build"):
        assert graph.way(way) is None, "Дубль области more6 слит с узлом графа: " + way
    assert graph.events["more6_spirits_short"]["tasks"] == [], \
        "Призыв духа — постройка жилья, а не действие духа"
    assert "building:family_house_1" in graph.events["more6_spirits_short"]["push"], \
        "Нехватка духов ведёт к постройке жилья"
    weak = graph.events["more6_weak_far"]["tasks"][0]["id"]
    assert weak == "more_return_before_night", "Слабый дух возвращается домой существующим путём: " + weak
    for way in MORE6_WAYS:
        path = graph.way(way)
        assert path is not None, "Python-модель не видит путь области more6: " + way
        assert path.makes in graph.items, "Python-модель не видит узел области more6: " + path.makes
        assert graph.items[path.makes].base > 0, "Стартовый вес узла more6 положителен: " + path.makes
        assert graph.implemented(way) == (way in MORE6_IMPLEMENTED), \
            "Путь области more6 либо связан с действием, либо честно помечен заглушкой: " + way
    print("OK: узлы области more6 читаются из фрагментов, дубли слиты")


def test_more6_inputs():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    tasks = {task["id"] for task in plan(graph, State({
        "stage": 1, "kits": {"spirit-1": {}}, "events": list(MORE6_EVENTS)}))["tasks"]}
    for event in MORE6_EVENTS:
        declared = graph.events[event]
        if not declared["tasks"]:
            continue
        assert any(task.startswith("event:" + event + ":") for task in tasks), \
            "Python: событие области more6 не даёт задания: " + event
    quiet = {task["id"] for task in plan(graph, State({"stage": 1, "kits": {"spirit-1": {}}}))["tasks"]}
    for way in MORE6_WAYS:
        assert not any(task.startswith(way) for task in quiet), \
            "Python: без входа области more6 задачи нет: " + way
    print("OK: входы области more6 учитываются в выборе")


MORE5_EVENTS = ("more5_crops_dark", "more5_saplings_dark",
                "more5_crops_dry", "more5_growth_unfed", "more5_fuel_low",
                "more5_build_shortage", "more5_mob_broken", "more5_route_blocked",
                "more5_chest_far", "more5_mine_dark", "more5_wood_drops")
MORE5_WAYS = ("more5_light_crops", "more5_light_saplings", "more5_water_crops",
              "more5_fertilize_growth", "more5_gather_fuel", "more5_make_charcoal",
              "more5_gather_build_material", "more5_craft_build_material",
              "more5_restore_block", "more5_bridge_route",
              "more5_place_site_chest", "more5_carry_to_site", "more5_light_mine",
              "more5_craft_mine_torch", "more5_pickup_wood_drop", "more5_plant_sapling")
MORE5_IMPLEMENTED = {
    "more5_light_crops",
    "more5_light_saplings", "more5_water_crops", "more5_fertilize_growth", "more5_gather_fuel",
    "more5_make_charcoal", "more5_gather_build_material",
    "more5_craft_build_material", "more5_restore_block", "more5_bridge_route",
    "more5_place_site_chest", "more5_carry_to_site", "more5_light_mine",
    "more5_craft_mine_torch", "more5_pickup_wood_drop", "more5_plant_sapling",
}


def test_more5_nodes():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    for event in MORE5_EVENTS:
        assert event in graph.events, "Python-модель не видит событие области graphmore5: " + event
    for way in MORE5_WAYS:
        assert graph.way(way) is not None, "Python-модель не видит путь области graphmore5: " + way
        assert graph.implemented(way) == (way in MORE5_IMPLEMENTED),             "Путь области graphmore5 либо связан с действием, либо честно помечен заглушкой: " + way
    for item in ("more5:growth_lit", "more5:fuel_ready",
                 "more5:build_supply", "more5:block_restored", "more5:route_opened",
                 "more5:site_chest", "more5:mine_lit", "more5:wood_drop"):
        assert item in graph.items and graph.items[item].base > 0, \
            "Python-модель не видит узел области graphmore5: " + item
    assert "#more5_fuel" in graph.groups, "Python-модель не видит группу топлива области more5"
    print("OK: узлы области graphmore5 читаются из фрагмента, заглушки отмечены")


def test_more5_inputs():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    state = State({
        "stage": 1,
        "kits": {"spirit-1": {}},
        "events": list(MORE5_EVENTS),
        "facts": {},
    })
    tasks = {task["id"]: task for task in plan(graph, state)["tasks"]}
    for event in MORE5_EVENTS:
        prefix = "event:" + event + ":"
        assert any(task.startswith(prefix) for task in tasks), \
            "Python: событие области graphmore5 не даёт задания: " + event
    for task_id in ("event:more5_crops_dark:more5_light_crops",
                    "event:more5_wood_drops:more5_pickup_wood_drop"):
        assert task_id in tasks, "Python: нет ожидаемого задания области graphmore5: " + task_id
        assert tasks[task_id]["implemented"], "Связанное действие области выдаётся готовым: " + task_id
    water = "event:more5_crops_dry:more5_water_crops"
    assert water in tasks and tasks[water]["implemented"],         "Полив связан с действием и выдаётся готовым: " + water
    assert "more5_player_weak" not in graph.events, \
        "Python: легаси-событие защиты игрока удалено, его ведёт life_threat_player"
    print("OK: входы области graphmore5 учитываются в выборе")


MORE4_EVENTS = (
    "more4_player_near", "more4_player_hungry", "more4_player_tool_broken",
    "more4_wrong_tool_hand", "more4_portal_found", "more4_portal_unsafe",
    "more4_tamed_pet", "more4_rare_found",
    "more4_rare_mixed", "more4_caution",
)
MORE4_WAYS = (
    "more4_guard_settlement", "more4_watch_chests", "more4_bring_player_food",
    "more4_lend_player_tool", "more4_switch_tool", "more4_guard_portal",
    "more4_fence_portal",
    "more4_guard_with_pet", "more4_store_rare",
    "more4_sort_rare", "more4_feed_hungry", "more4_rest_injured",
)


def test_more4_nodes():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    for event in MORE4_EVENTS:
        assert event in graph.events, "Python-модель не видит событие области more4: " + event
    for way in ("more4_plan_expansion", "more4_find_space", "more4_leave_pet_alone", "more4_spare_rare"):
        assert graph.way(way) is None, "Правило выбора области more4 живёт в механике: " + way
    for way in MORE4_WAYS:
        assert graph.way(way) is not None, "Python-модель не видит путь области more4: " + way
        assert graph.implemented(way), "Состояние действия области more4: " + way
    for item in ("more4:settlement_guarded", "more4:player_helped", "more4:tool_ready",
                 "more4:portal_guarded", "more4:pet_guarded",
                 "more4:rare_kept", "more4:caution"):
        assert item in graph.items, "Python-модель не видит новый узел области graphmore4: " + item
        assert graph.items[item].base > 0, "Стартовый вес узла more4 положителен: " + item
    assert "#rare" in graph.groups, "Python-модель не видит группу редких предметов"
    print("OK: узлы и события области graphmore4 читаются из фрагмента")


def test_more4_inputs():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    tasks = {task["id"] for task in plan(graph, State({
        "stage": 1, "kits": {"spirit-1": {}}, "events": list(MORE4_EVENTS)}))["tasks"]}
    for event in MORE4_EVENTS:
        assert any(task.startswith("event:" + event + ":") for task in tasks),             "Python: событие области more4 не даёт задания: " + event
    quiet = {task["id"] for task in plan(graph, State({
        "stage": 1, "kits": {"spirit-1": {}}, "events": []}))["tasks"]}
    for way in MORE4_WAYS:
        assert way not in quiet, "Python: без входа области more4 задачи нет: " + way
    print("OK: входы области graphmore4 учитываются в выборе")


MORE7 = {
    "more7:harvest_peak": "more7_harvest_early",
    "more7:return_peak": "more7_return_home",
    "more7:pen_room": "more7_expand_pen",
    "more7:order_delivered": "more7_order_to_chest",
}


def test_more7_nodes():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    events = ("more7_morning_harvest",
              "more7_evening_return", "more7_pen_full", "more7_field_full",
              "more7_order_ready")
    ways = ("more7_harvest_early",
            "more7_return_home", "more7_expand_pen",
            "more7_order_to_chest")
    for event in events:
        assert event in graph.events, "Python-модель не видит событие области graphmore7: " + event
    for way in ("more7_spread_work", "more7_defer_gated", "more7_hold_herd", "more7_hold_sowing",
                "more7_expand_field"):
        assert graph.way(way) is None, "Правило выбора области more7 живёт в механике: " + way
    assert graph.events["more7_field_full"]["tasks"] == [], \
        "Расширение поля — постройка field_2, а не действие духа"
    assert "building:field_2" in graph.events["more7_field_full"]["push"], \
        "Полное поле ведёт к следующему полю плана"
    implemented = {"more7_harvest_early", "more7_expand_pen", "more7_return_home",
                   "more7_order_to_chest"}
    for way in ways:
        assert graph.way(way) is not None, "Python-модель не видит путь области graphmore7: " + way
        assert graph.implemented(way) == (way in implemented), \
            "Путь области graphmore7 либо связан с действием, либо честно помечен заглушкой: " + way
    for item, way in MORE7.items():
        assert item in graph.items, "Python-модель не видит узел области graphmore7: " + item
        assert graph.items[item].base > 0, "Стартовый вес узла more7 положителен: " + item
        assert graph.way(way) is not None, "Путь узла more7 объявлен: " + way
    print("OK: узлы и события области graphmore7 читаются из фрагмента")


def test_more7_inputs():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    events = ["more7_morning_harvest", "more7_evening_return", "more7_pen_full",
              "more7_field_full", "more7_order_ready"]
    tasks = {task["id"] for task in plan(graph, State({
        "stage": 1, "kits": {"spirit-1": {}}, "events": events}))["tasks"]}
    for event in events:
        declared = graph.events[event]
        if not declared["tasks"]:
            continue
        assert any(task.startswith("event:" + event + ":") for task in tasks), \
            "Python: событие области graphmore7 не даёт задания: " + event
    quiet = {task["id"] for task in plan(graph, State({
        "stage": 1, "kits": {"spirit-1": {}}, "events": []}))["tasks"]}
    for way in MORE7.values():
        assert way not in quiet, "Python: без входа области graphmore7 задачи нет: " + way
    print("OK: входы области graphmore7 учитываются в выборе")


def test_block_feedback():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    for index, world in enumerate(sim_gpu.failure_worlds(graph)):
        outcome = sim_gpu.run(world, limit=4000)
        assert outcome["repeat_blocked"] == 0, \
            "Заблокированная задача поставлена повторно в мире " + str(index) + ": " + str(outcome)
        assert outcome["key_raised"], \
            "Ключевая задача не поднята выше прежнего приоритета в мире " + str(index) + ": " + str(outcome)
        assert outcome["other_work"] > 0, \
            "Прочая работа простаивает при блокировке в мире " + str(index) + ": " + str(outcome)
    print("OK: блокировка не повторяется, ключ поднят, прочая работа идёт")


def test_baseline_trail():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    outcome = sim_gpu.run(sim_gpu.baselines(graph)[0], limit=4000)
    assert outcome["steps"] == 1043 and outcome["ticks"] == 197660, \
        "Пустой мир обязан повторить Java (1043 шага, 197 660 тиков), а не решение линейной системы: " \
        + str((outcome["steps"], outcome["ticks"]))
    print("OK: пустой мир совпадает с Java (расходящийся поток решается итерациями)")


def test_audit_inputs():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    state = State({
        "stage": 2,
        "kits": {"spirit-1": {}},
        "buildings": {"hut": "BUILT"},
        "events": ["repair_hut"],
        "facts": {
            "buildings": {"hut": {"id": "hut", "damage": 5, "auditPercent": 50,
                                  "missing": {"minecraft:dirt": 3}, "extra": {}, "wrong": 0,
                                  "unknown": False}},
            "stageReqs": {"minimum": {"hut": 1}, "actual": {}},
        },
    })
    planner = Planner(graph, state)
    assert planner.materials("hut") == {"dirt": 3},         "Недостача аудита превращается в материалы ремонта: " + str(planner.materials("hut"))
    task = graph.events["repair_hut"]["tasks"][0]
    assert planner.event_weight(task) > int(task["base"]),         "Ремонт повреждённой постройки важнее базового веса события"
    unknown = State({
        "stage": 2,
        "kits": {"spirit-1": {}},
        "buildings": {"hut": "BUILT"},
        "facts": {"buildings": {"hut": {"id": "hut", "damage": 5, "auditPercent": 50,
                                        "missing": {"minecraft:dirt": 3}, "unknown": True}}},
    })
    assert Planner(graph, unknown).materials("hut") == graph.buildings["hut"].materials,         "Неизвестный аудит не подменяет материалы схемы"
    print("OK: аудит построек даёт материалы и усиливает ремонт")


def test_event_scaling():
    graph = Graph({
        "maxPriority": 100,
        "items": {},
        "buildings": {},
        "ways": [],
        "events": {
            "life_threat": {
                "life": True,
                "tasks": [
                    {"id": "defend", "case": "ACTIVITY_DEFEND", "base": 120, "consumes": {}},
                    {"id": "sleep", "case": "ACTIVITY_REST", "base": 101, "consumes": {}},
                ],
            }
        },
    })
    scaled = graph.scaled({"events": {"life_threat": 0.5}})
    assert scaled.events["life_threat"]["tasks"][0]["base"] == 60, \
        "Множитель события масштабирует стартовый вес: 120 / 2"
    assert scaled.events["life_threat"]["tasks"][1]["base"] == 51, \
        "Множитель события масштабирует все задачи жизни: 101 / 2 с округлением"
    assert graph.events["life_threat"]["tasks"][0]["base"] == 120, \
        "Масштабирование не меняет исходный граф"
    print("OK: множители событий меняют стартовые веса")


def test_input_worlds():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    worlds = dict(sim_gpu.input_worlds(graph))
    expected = {
        "life": "event:life_threat",
        "store": "store_smelt_take",
        "world": "event:backfill",
        "gear": "event:gear_craft_pickaxe",
        "more": "more_help_player",
        "more2": "event:more2_fire_near_building",
        "more3": "event:danger_spot",
        "more4": "event:more4_player_near",


        "more7": "event:more7_order_ready",


        "more6": "event:more6_weak_far",
    }
    for name, prefix in expected.items():
        assert name in worlds, "Стартовый мир области не собран: " + name
        tasks = [task["id"] for task in worlds[name].plan()["tasks"] if task["feasible"]]
        assert any(task.startswith(prefix) or prefix in task for task in tasks), \
            "Мир '" + name + "' не даёт ожидаемого задания " + prefix + ": " + str(tasks)
    outcome = sim_gpu.run(worlds["dig"], limit=200)
    dig = [step[1] for step in outcome["trail"]]
    assert any(task in ("dig_out", "bridge", "pillar") for task in dig), \
        "Мир 'dig' не даёт дорогого пути выхода: " + str(dig)
    assert "restore_trace" in dig, "Выход из ямы обязан требовать возврата следа: " + str(dig)
    print("OK: стартовые миры областей дают задания новых узлов")


def test_input_effects():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    worlds = dict(sim_gpu.input_worlds(graph))
    world = worlds["world"]
    world.after({"id": "event:backfill:backfill", "makes": "backfill"})
    assert world.facts["terrain"]["pits"] == 1, "Засыпанная яма уменьшает их число на входе"
    world.after({"id": "event:repair_hut:repair:hut", "makes": "repair:hut"})
    assert world.facts["buildings"]["hut"]["damage"] == 0, "Ремонт снимает повреждение постройки"
    dig = worlds["dig"]
    before = sum(1 for spirit in dig.facts["spirits"].values() if spirit.get("trapped"))
    dig.after({"id": "dig_out", "makes": "dig_open"})
    after = sum(1 for spirit in dig.facts["spirits"].values() if spirit.get("trapped"))
    assert after == before - 1, "Выход из ловушки освобождает одного духа"
    store = worlds["store"]
    store.after({"id": "store_carry_deliver", "makes": "store:carry:deliver"})
    assert all(not spirit.get("inventory") for spirit in store.facts["spirits"].values()), \
        "Доставка опустошает руки духов"
    store.after({"id": "store_smelt_take", "makes": "store:smelt:take"})
    assert store.facts["stations"]["furnace"]["output"] is None, "Выгрузка очищает выход печи"
    print("OK: завершение новых заданий обновляет вход, а не повторяет причину")


def test_dead_inputs():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    worlds = dict(sim_gpu.input_worlds(graph))
    farm = [step[1] for step in sim_gpu.run(worlds["farm"], limit=1600)["trail"]]
    for way in ("restock_food", "till_soil", "milk_animal"):
        assert way in farm, "Мир фермы обязан вызвать путь '" + way + "': " + str(farm)
    more5 = [step[1] for step in sim_gpu.run(worlds["more5"], limit=1600)["trail"]]
    for way in ("more5_light_crops", "more5_pickup_wood_drop", "more5_light_mine"):
        assert any(way in task for task in more5),             "Мир more5 обязан вызвать путь '" + way + "': " + str(more5)
    area = [step[1] for step in sim_gpu.run(worlds["world"], limit=1600)["trail"]]
    repairs = {name for name in worlds["world"].state().events if name.startswith("repair_")}
    assert repairs and any(task.startswith("event:repair_") for task in area),             "Мир участка обязан вызвать ремонт повреждённой постройки: " + str(sorted(repairs))
    for name, expected in (("gear", {"gear_kit_mining", "gear_kit_woodcutting",
                                     "gear_kit_farming", "gear_kit_building"}),
                           ("farm", {"crop_dry", "pen_full"}),
                           ("life", {"life_injury"})):
        events = set(worlds[name].state().events)
        missing = expected - events
        assert not missing, "Вход мира '" + name + "' не подаёт события: " + str(sorted(missing))
    print("OK: мёртвые входы областей вызываются мирами (ферма, ремонт, снаряжение, жизнь)")


def test_new_input_worlds():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    worlds = dict(sim_gpu.input_worlds(graph))
    dirty = [step[1] for step in sim_gpu.run(worlds["dirty"], limit=1600)["trail"]]
    for prefix in ("event:demolish_stray:", "event:backfill:", "event:level_lot:",
                   "event:clear_floating:", "event:repair_"):
        assert any(task.startswith(prefix) for task in dirty),             "Грязный мир обязан вызвать расчистку/ремонт: " + prefix + " в " + str(dirty[:20])
    blocked_world = worlds["blocked"]
    blocked = [step[1] for step in sim_gpu.run(blocked_world, limit=1600)["trail"]]
    assert blocked.count("rescue_spirit") == 1, "Провал пути даёт одно спасение, а не цикл: " + str(blocked)
    state = blocked_world.state()
    for tag in ("more:player_near", "more:dusk_soon", "more:bad_trip_conditions", "more:failures_repeat"):
        assert tag in state.world, "Обратная связь мира обязана дать метку " + tag
    field = [step[1] for step in sim_gpu.run(worlds["field"], limit=1600)["trail"]]
    assert "building:wheat_field" in field and any("plant_crop" in task for task in field),         "Мир грядки обязан строить wheat_field и сажать: " + str(field)
    print("OK: грязный мир, провалы путей и грядка изображаются симуляцией")


def test_live_stage_one():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    world = sim_gpu.live_stage_one(graph, os.path.join(EXPORT, "live1.json"))
    start = {task["id"]: task for task in world.plan()["tasks"]}
    assert "soil" in world.world, "Живой этап 1 берёт землю с карьера"
    assert "dig_dirt" in start and start["dig_dirt"]["feasible"], \
        "Карьер обязан давать землю: " + str(start.get("dig_dirt"))
    assert "building:mine" in start and not start["building:mine"]["feasible"], \
        "Шахта без кирки не выполняется, пока инструмент не скрафчен"
    outcome = sim_gpu.run(world, limit=4000)
    assert outcome["finished"] and outcome["stage"] == 2, \
        "Живой этап 1 не закрылся: " + str(outcome["stuck"])
    assert 1 in outcome["stage_ticks"], "Закрытие этапа 1 не отметилось: " + str(outcome["stage_ticks"])
    assert set(outcome["stage_steps"]) == {1}, \
        "Мир живой этап 1 должен останавливаться на этапе 2: " + str(outcome["stage_steps"])
    steps = [task for stage, task, _priority in outcome["trail"] if stage == 1]
    for task in ("building:mine", "building:streets", "building:orchard_oak", "building:wheat_field"):
        assert task in steps, "Этап 1 закрыт без '" + task + "': " + str(steps)
    pickaxe = next((index for index, task in enumerate(steps) if "pickaxe" in task), -1)
    assert pickaxe >= 0 and steps.index("building:mine") > pickaxe, \
        "Кирка должна появиться до шахты: " + str(steps)
    assert steps.count("rescue_spirit") == 2, \
        "Два застрявших духа дают ровно два спасения, а не цикл: " + str(Counter(steps))
    assert steps.count("dig_dirt") >= 8, \
        "Закрытая добыча земли переоткрывается, пока улицам нужна земля: " + str(Counter(steps))
    assert any(task.startswith("event:demolish_stray") for task in steps), \
        "Этап 1 обходит лишние постройки: " + str(steps)
    assert any(task.startswith("event:clear_floating") for task in steps), \
        "Этап 1 обходит висящее: " + str(steps)
    assert len(steps) <= 200, "Этап 1 зациклился: " + str(len(steps)) + " заданий"
    repeated = Counter(steps).most_common(1)[0][1]
    assert repeated <= 60, "Одно задание повторяется без продвижения: " + str(repeated)
    print("OK: живой этап 1 закрывается за " + str(len(steps)) + " заданий")


def test_base_worlds():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    with open(sim_gpu.bases_path(os.path.join(EXPORT, "bases.json")), "r", encoding="utf-8") as handle:
        expected = json.load(handle)
    for name in sim_gpu.BASE_NAMES:
        world = sim_gpu.base_world(graph, name, os.path.join(EXPORT, "bases.json"))
        outcome = sim_gpu.run(world, limit=int(expected[name]["limit"]))
        assert bool(outcome["finished"]) == bool(expected[name]["finished"]), \
            "Базовый мир '" + name + "': финиш Python " + str(outcome["finished"]) \
            + " != Java " + str(expected[name]["finished"])
        assert outcome["steps"] == int(expected[name]["steps"]), \
            "Базовый мир '" + name + "': шагов Python " + str(outcome["steps"]) \
            + " != Java " + str(expected[name]["steps"])
        assert outcome["stage"] == int(expected[name]["stage"]), \
            "Базовый мир '" + name + "': этап Python " + str(outcome["stage"]) \
            + " != Java " + str(expected[name]["stage"])
        trail = [{"id": task, "priority": priority} for _stage, task, priority in outcome["trail"]]
        assert trail == expected[name]["trail"], \
            "Базовый мир '" + name + "': путь Python разошёлся с Java"

    dirty = [task for _stage, task, _priority in sim_gpu.run(
        sim_gpu.base_world(graph, "dirty", os.path.join(EXPORT, "bases.json")),
        limit=1600)["trail"]]
    for prefix in ("event:demolish_stray:", "event:backfill:", "event:clear_floating:", "event:repair_"):
        assert any(task.startswith(prefix) for task in dirty), \
            "Грязный базовый мир обязан звать уборку: " + prefix + " в " + str(dirty[:20])

    night_world = sim_gpu.base_world(graph, "night", os.path.join(EXPORT, "bases.json"))
    night = {task["id"]: task for task in night_world.plan()["tasks"]}
    assert "event:life_threat:defend" in night and night["event:life_threat:defend"]["feasible"], \
        "Ночь с угрозой обязана защищаться: " + str(sorted(night)[:20])
    assert "event:expedition_ready:expedition" in night, "Поход выдаёт только узел графа"
    assert not any(task.startswith("chop_") and task_info["feasible"] and task_info["makes"] == "oak_log"
                   for task, task_info in night.items()), \
        "Ночью далёкая рубка не выполнима"

    failures_world = sim_gpu.base_world(graph, "failures", os.path.join(EXPORT, "bases.json"))
    failed = {task["id"]: task for task in failures_world.plan()["tasks"]}
    assert "chop_oak_log" in failed, "Провал не убирает путь из плана: " + str(sorted(failed)[:20])

    noaxe = sim_gpu.run(sim_gpu.base_world(graph, "noaxe", os.path.join(EXPORT, "bases.json")),
                        limit=4000)["trail"]
    steps = [task for _stage, task, _priority in noaxe]
    first_wood = next((index for index, task in enumerate(steps)
                       if task.startswith(("chop_jungle_log", "chop_oak_log"))), -1)
    first_axe = next((index for index, task in enumerate(steps) if "axe@" in task), -1)
    assert first_wood >= 0, "Пустой старт без топора рубит дерево руками: " + str(steps[:10])
    assert first_axe < 0 or first_wood < first_axe, \
        "Топор не ждёт себя: рубка " + str(first_wood) + ", топор " + str(first_axe)
    print("OK: базовые миры совпадают с Java (живой, грязный, провалы, ночь, без топора)")


def test_tool_condition_and_soil():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    bare = sim_gpu.World.settled(graph).with_item("cobblestone", 0).tools("spirit-1", -1)
    tasks = {task["id"]: task for task in bare.plan()["tasks"]}
    assert not tasks["mine_cobble"]["feasible"], "Без кирки булыжник не добывается"
    assert any("pickaxe" in task for task in tasks), "Без кирки граф ставит её крафт: " + str(tasks)
    armed = bare.copy().tools("spirit-1", 0)
    armed_tasks = {task["id"]: task for task in armed.plan()["tasks"]}
    assert armed_tasks["mine_cobble"]["feasible"], "С деревянной киркой булыжник добывается"
    no_soil = sim_gpu.World.empty(graph)
    no_soil.world.discard("soil")
    no_soil.built("route_dirt", BUILT)
    no_soil_tasks = {task["id"]: task for task in no_soil.plan()["tasks"]}
    assert not no_soil_tasks.get("dig_dirt", {}).get("feasible", False), \
        "Без источника soil карьер не выполняется"
    assert not no_soil_tasks["dig_dirt_route"]["feasible"], "Путь с лопатой без лопаты не выполняется"
    assert any("shovel" in task for task in no_soil_tasks), "Без лопаты граф ставит её крафт"
    no_soil_armed = no_soil.copy().tools("spirit-1", 0)
    route = {task["id"]: task for task in no_soil_armed.plan()["tasks"]}["dig_dirt_route"]
    assert route["feasible"], "С деревянной лопатой земля берётся с пути"
    print("OK: инструмент — условие пути, земля берётся с soil, без soil остаётся путь с лопатой")


def test_stage_two_worlds():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    worlds = [("stage2-full", sim_gpu.baselines(graph)[3]),
              ("stage2-all", search.make_world(graph, 2))]
    for name, world in worlds:
        unbuilt = sim_gpu.unbuilt(world)
        assert "warehouse" in unbuilt and "pen_cow" in unbuilt,             "Склад и загон строит граф, они обязаны держать этап: " + str(unbuilt)
        outcome = sim_gpu.run(world, limit=4000)
        assert outcome["stage"] >= 3,             "Мир " + name + " обязан пройти этап 2 без сторожей: " + outcome["stuck"]
    print("OK: миры этапа 2 доходят до этапа 3 (склад и загоны не держат этап)")


def test_warehouse_decision():
    """Склад строит только решение стратега: без него узел остаётся заявкой, этап не закрывается."""
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    world = sim_gpu.World.settled(graph)
    world.stage = 2
    world.warehouse_order = 0
    for building in graph.buildings.values():
        if building.stage < 2:
            world.buildings[building.id] = BUILT
        elif building.stage == 2 and not building.optional:
            world.buildings[building.id] = PLANNED
        else:
            world.buildings[building.id] = ABSENT
    world.buildings["warehouse"] = ABSENT
    world.buildings["warehouse_2"] = ABSENT

    tasks = {task["id"]: task for task in world.plan()["tasks"]}
    assert "building:warehouse" not in tasks, \
        "Без решения стратега граф не ставит склад: " + str(sorted(tasks))
    assert any(notice.get("building") == "warehouse" for notice in world.plan()["notices"]), \
        "Склад без решения остаётся заявкой стратегу: " + str(world.plan()["notices"])
    outcome = sim_gpu.run(world.copy(), limit=1600)
    assert not outcome["finished"] and outcome["stage"] == 2, \
        "Без решения стратега этап 2 не закрывается складом: " + str(outcome["stuck"])

    decided = world.copy()
    decided.warehouse_order = 4
    decided.buildings["warehouse"] = PLANNED
    tasks = {task["id"]: task for task in decided.plan()["tasks"]}
    assert "building:warehouse" in tasks, \
        "С решением стратега граф ставит склад: " + str(sorted(tasks))
    outcome = sim_gpu.run(decided.copy(), limit=4000)
    assert outcome["finished"] and outcome["stage"] >= 3, \
        "С решением стратега этап 2 закрывается складом: " + str(outcome["stuck"])
    print("OK: решение стратега о складе — факт модели: без него склад не строится")


def test_failure_memory():
    """Память неудач в Python: цель отдельно, узел по разным целям (GraphFailureMemoryTests)."""
    base = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)

    def dusk(**choice):
        graph = base
        if choice:
            data = copy.deepcopy(base.raw)
            data.setdefault("choice", {}).update(choice)
            graph = Graph(data)
        world = sim_gpu.World.settled(graph)
        world.world.add("more:dusk_soon")
        world.distances["more:dusk_soon"] = 40
        return world

    def failures(world, entries):
        world.with_facts({"feedback": {"failures": [
            {"node": "more_return_before_night", "target": target, "count": count,
             "need": need, "lastTick": 100}
            for target, count, need in entries]}})
        return world

    def priority_of(world):
        return next(t["priority"] for t in world.plan()["tasks"]
                    if t["id"] == "more_return_before_night")

    calm = dusk()
    calm_priority = priority_of(calm)
    one = failures(dusk(), [("0,64,0", 5, {"point": "0,64,0"})])
    assert priority_of(one) == calm_priority, \
        "Python: один недоступный ствол не штрафует рубку везде: %s/%s" % (
            priority_of(one), calm_priority)

    many = failures(dusk(), [("0,64,0", 3, {"point": "0,64,0"}), ("8,64,8", 3, {"point": "8,64,8"})])
    assert priority_of(many) < calm_priority, \
        "Python: провал разных целей снижает вес узла: %s/%s" % (priority_of(many), calm_priority)

    faded = dusk()
    faded.with_facts({"feedback": {"failures": [
        {"node": "more_return_before_night", "target": target, "count": 1, "weight": 0.1,
         "need": {"point": target}, "lastTick": 100}
        for target in ("0,64,0", "8,64,8")]}})
    assert priority_of(many) < priority_of(faded) <= calm_priority, \
        "Python: затухший штраф слабее свежего и не превышает спокойный вес: %s/%s/%s" % (
            priority_of(many), priority_of(faded), calm_priority)

    chunk = failures(dusk(), [("0,64,0", 5, None)])
    assert priority_of(chunk) == calm_priority, \
        "Python: незагруженный чанк не штрафует узел: %s/%s" % (priority_of(chunk), calm_priority)
    material = failures(dusk(), [("0,64,0", 5, {"item": "dirt", "count": 1})])
    assert priority_of(material) == calm_priority, \
        "Python: нет материала — нехватка снабжения, а не штраф узла: %s/%s" % (
            priority_of(material), calm_priority)
    other = failures(dusk(), [("0,64,0", 2, None)])
    assert priority_of(other) == calm_priority, \
        "Python: провал без нужды не штрафует узел: %s/%s" % (
            priority_of(other), calm_priority)

    calm_ticks = next(t["ticks"] for t in calm.plan()["tasks"]
                      if t["id"] == "more_return_before_night")
    far_one = failures(dusk(farTrip=5.0), [("0,64,0", 1, {"point": "0,64,0"})])
    far_one_ticks = next(t["ticks"] for t in far_one.plan()["tasks"]
                         if t["id"] == "more_return_before_night")
    assert far_one_ticks == calm_ticks, \
        "Python: одна недоступная цель не удлиняет дорогу всему узлу: %s/%s" % (
            far_one_ticks, calm_ticks)
    far_many = failures(dusk(farTrip=5.0), [("0,64,0", 1, {"point": "0,64,0"}), ("8,64,8", 1, {"point": "8,64,8"})])
    far_many_ticks = next(t["ticks"] for t in far_many.plan()["tasks"]
                          if t["id"] == "more_return_before_night")
    assert far_many_ticks == 2200, \
        "Python: провал разных целей удлиняет дорогу по farTrip: " + str(far_many_ticks)

    dangers = life_facts(health=4, targets=[("spirit-1", 2), ("spirit-1", 3)])
    life = sim_gpu.World.settled(base).event("life_threat").with_facts(dangers)
    calm_life = next(t["priority"] for t in life.plan()["tasks"]
                     if t["id"] == "event:life_threat:defend")
    life_failed = life.copy()
    failed_facts = life_facts(health=4, targets=[("spirit-1", 2), ("spirit-1", 3)])
    failed_facts["feedback"] = {"failures": [
        {"node": "event:life_threat:defend", "target": "0,64,0", "count": 3,
         "cause": "NO_PATH", "lastTick": 100}]}
    life_failed.with_facts(failed_facts)
    failed_life = next(t["priority"] for t in life_failed.plan()["tasks"]
                       if t["id"] == "event:life_threat:defend")
    assert failed_life == calm_life, \
        "Python: защита жизни не штрафуется памятью неудач: %s/%s" % (failed_life, calm_life)
    print("OK: память неудач штрафует цель отдельно, узел — по разным целям в Python")


def test_choice_mechanics():
    """Правила выбора в Python: те же поправки, что и в Java (GraphChoiceTests)."""
    base = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)

    def tuned(**choice):
        data = copy.deepcopy(base.raw)
        data.setdefault("choice", {}).update(choice)
        return Graph(data)

    def far(graph):
        world = sim_gpu.World.settled(graph)
        world.world.add("more:dusk_soon")
        world.distances["more:dusk_soon"] = 40
        return world

    calm = far(base)
    harsh = far(tuned(farTrip=3.0))
    harsh.world.add("more:bad_trip_conditions")
    assert calm.plan()["tasks"]
    neutral_tick = next(t["ticks"] for t in calm.plan()["tasks"]
                        if t["id"] == "more_return_before_night")
    harsh_tick = next(t["ticks"] for t in harsh.plan()["tasks"]
                      if t["id"] == "more_return_before_night")
    assert (neutral_tick, harsh_tick) == (600, 1400), \
        "Python: непогода утраивает дорогу: %s/%s" % (neutral_tick, harsh_tick)
    slow = sim_gpu.World.settled(tuned(slowWork=2.0))
    slow.world.add("more:dusk_soon")
    slow.events.add("work_yield_low")
    slow_tick = next(t["ticks"] for t in slow.plan()["tasks"]
                     if t["id"] == "more_return_before_night")
    assert slow_tick == 400, "Python: медленная работа удваивает срок: " + str(slow_tick)

    secure = sim_gpu.World.settled(base)
    secure.world.update({"more:fence_gap", "more:door_open_night", "more:hopper_uncovered"})
    neutral = min(t["priority"] for t in secure.plan()["tasks"]
                  if t["id"] in ("more_secure_fence", "more_secure_door", "more_secure_hopper"))
    duplicate = sim_gpu.World.settled(tuned(duplicate=0.5))
    duplicate.world.update({"more:fence_gap", "more:door_open_night", "more:hopper_uncovered"})
    duplicate.world.add("more:work_duplicate")
    penalized = min(t["priority"] for t in duplicate.plan()["tasks"]
                    if t["id"] in ("more_secure_fence", "more_secure_door", "more_secure_hopper"))
    assert penalized < neutral, "Python: дубль работы дешевле: %s/%s" % (penalized, neutral)

    goal = sim_gpu.World.settled(base)
    goal.world.update({"more:player_near", "more:strategic_goal"})
    goal_boosted = sim_gpu.World.settled(tuned(strategicBoost=10.0))
    goal_boosted.world.update({"more:player_near", "more:strategic_goal"})
    plain = next(t["priority"] for t in goal.plan()["tasks"] if t["id"] == "more_help_player")
    raised = next(t["priority"] for t in goal_boosted.plan()["tasks"] if t["id"] == "more_help_player")
    assert raised > plain, "Python: стратегическая цель поднимает вес: %s/%s" % (raised, plain)

    space = sim_gpu.World.empty(tuned(spaceFoundBoost=50.0))
    space.events.add("more4_space_found")
    tight = sim_gpu.World.empty(tuned(spaceTight=0.3))
    tight.events.add("more4_space_tight")
    quiet = sim_gpu.World.empty(base)
    fence = lambda world: next(t["priority"] for t in world.plan()["tasks"]
                               if t["id"] == "building:fence")
    assert fence(space) > fence(quiet) > fence(tight), \
        "Python: свободное место поднимает стройку, тесное опускает"

    herd = sim_gpu.World.empty(base)
    herd.stage = 2
    herd.buildings["pen_cow"] = BUILT
    herd.events.add("more7_pen_full")
    held = sim_gpu.World.empty(tuned(herdFull=0.5))
    held.stage = 2
    held.buildings["pen_cow"] = BUILT
    held.events.add("more7_pen_full")
    breed = lambda world: next(t["priority"] for t in world.plan()["tasks"] if t["id"] == "breed_cow")
    assert breed(held) < breed(herd), "Python: полный загон сдерживает разведение"

    field = sim_gpu.World.settled(base)
    field.events.update({"more2_food_low", "more7_field_full"})
    full = sim_gpu.World.settled(tuned(fieldFull=0.5))
    full.events.update({"more2_food_low", "more7_field_full"})
    sow = "event:more2_food_low:more2_sow_food"
    assert next(t["priority"] for t in full.plan()["tasks"] if t["id"] == sow) \
        < next(t["priority"] for t in field.plan()["tasks"] if t["id"] == sow), \
        "Python: полное поле сдерживает посев"
    print("OK: поправки выбора (дорога, срок, дубль, цель, место, загон, поле) работают в Python")


def test_recipe_weights():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    params = search.parameters(graph)
    weights = search.vector_to_weights(graph, [1.0] * len(params), params)
    frozen = sorted(way for way in weights["ways"] if way.startswith("auto_"))
    assert not frozen, ("авто-рецепты пересобирает graphExport, их веса нельзя подбирать: "
                        + ", ".join(frozen[:5]))
    assert any(way.id.startswith("auto_") for way in graph.ways), \
        "в графе нет auto_*-рецептов: проверка заморозки ничего не доказала"
    print("OK: веса auto_*-рецептов заморожены, поиск их не подбирает")


def test_best_weights():
    path = os.path.join(HERE, "best-weights.json")
    with open(path, "r", encoding="utf-8") as handle:
        weights = json.load(handle)
    stale = sorted(
        key
        for section in ("weights", "absolute")
        for key in (weights.get(section) or {}).get("ways", {})
        if key.startswith("auto_"))
    assert not stale, ("в best-weights.json исторические auto_*-ключи: graphExport пересобирает эти пути, "
                       "их веса в файле мёртвые и вводят в заблуждение: " + ", ".join(stale[:5]))
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    known = {
        "items": set(graph.items),
        "events": set(graph.events),
        "ways": {way.id for way in graph.ways},
        "buildings": set(graph.buildings),
    }
    dead = []
    for section in ("weights", "absolute"):
        for kind, ids in known.items():
            dead.extend(
                section + "." + kind + ":" + key
                for key in (weights.get(section) or {}).get(kind, {})
                if key not in ids)
    assert not dead, ("в best-weights.json ключи, которых нет в графе: их веса мёртвые и вводят в заблуждение: "
                      + ", ".join(sorted(dead)[:5]))
    print("OK: в best-weights.json нет исторических auto_*-ключей и ключей вне графа")


def test_conformance():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    with open(os.path.join(EXPORT, "conformance.json"), "r", encoding="utf-8") as handle:
        cases = json.load(handle)
    failures = []
    for index, case in enumerate(cases):
        problem = compare_case(graph, case, index)
        if problem:
            failures.append(problem)
    assert not failures, f"расхождений {len(failures)} из {len(cases)}:\n" + "\n".join(failures[:10])
    print(f"OK: {len(cases)} случаев совпали с Java")


if __name__ == "__main__":
    try:
        test_fragments()
        test_life_priority()
        test_life_player_fact()
        test_food_need_band()
        test_dig_inputs()
        test_area_nodes()
        test_store_nodes()
        test_world_fragment()
        test_world_inputs()
        test_gear_fragment()
        test_gear_inputs()
        test_more_nodes()
        test_more_inputs()
        test_more2_nodes()
        test_more2_inputs()
        test_more3_nodes()
        test_more6_nodes()
        test_more6_inputs()
        test_more5_nodes()
        test_more5_inputs()
        test_more4_nodes()
        test_more4_inputs()
        test_more7_nodes()
        test_more7_inputs()
        test_block_feedback()
        test_baseline_trail()
        test_audit_inputs()
        test_event_scaling()
        test_input_worlds()
        test_input_effects()
        test_dead_inputs()
        test_new_input_worlds()
        test_live_stage_one()
        test_base_worlds()
        test_tool_condition_and_soil()
        test_stage_two_worlds()
        test_warehouse_decision()
        test_failure_memory()
        test_choice_mechanics()
        test_recipe_weights()
        test_best_weights()
        test_conformance()
    except AssertionError as error:
        print(error)
        sys.exit(1)
