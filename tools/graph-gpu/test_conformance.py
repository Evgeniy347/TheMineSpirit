"""Сверка Python-модели с Java на выгрузке conformance.json.

Запуск: python test_conformance.py
Выгрузка: ./gradlew graphExport -> TestResults/graph-export/{graph,conformance}.json

Критерий: набор заданий и feasible совпадают точно, priority расходится не больше чем на 1.
"""

import json
import os
import sys
import tempfile

from graph_model import Graph, State, plan

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
EXPORT = os.path.join(ROOT, "TestResults", "graph-export")
RECIPES = os.path.join(ROOT, "src", "main", "resources", "data", "theminespirit", "board",
                       "graph-recipes.json")
FRAGMENTS = os.path.join(ROOT, "src", "main", "resources", "data", "theminespirit", "board",
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


def test_life_band():
    graph = Graph({
        "maxPriority": 100,
        "items": {},
        "buildings": {},
        "ways": [],
        "events": {
            "life_threat": {
                "band": 20,
                "tasks": [
                    {"id": "defend", "case": "ACTIVITY_DEFEND", "base": 120, "consumes": {}},
                    {"id": "sleep", "case": "ACTIVITY_REST", "base": 101, "consumes": {}},
                ],
            }
        },
    })
    tasks = plan(graph, State({"stage": 1, "events": ["life_threat"], "kits": {"spirit-1": {}}}))["tasks"]
    by_id = {task["id"]: task["priority"] for task in tasks}
    defend = by_id.get("event:life_threat:defend")
    sleep = by_id.get("event:life_threat:sleep")
    assert defend is not None and sleep is not None, "Python-модель обязана видеть задания события жизни: " + str(tasks)
    assert defend > 100 and sleep > 100, "Полоса жизни выше maxPriority и в Python: " + str(by_id)
    assert defend >= sleep, "Защита важнее сна и в Python: " + str(by_id)
    print("OK: полоса жизни выше maxPriority в Python")


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
        os.path.join(ROOT, "src", "main", "resources", "data", "theminespirit", "board", "graph.json"),
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
            "strays": [{"id": "s", "type": "wall", "position": {"x": 1, "y": 64, "z": 1}}],
            "terrain": {"pits": 1, "hanging": 1, "heightDrop": 2, "deepestPit": 3, "traces": 1},
            "buildings": {"hut": {"id": "hut", "damage": 5, "unknown": False}},
        },
    })
    for node in ("demolish_stray", "backfill", "clear_floating", "level_lot", "restore_trace", "repair_hut"):
        assert node in state.events, "Вход участка добавляет узел '" + node + "' в выбор"
    unknown = State({"stage": 1, "kits": {"spirit-1": {}}, "facts": {
        "buildings": {"hut": {"id": "hut", "damage": 5, "unknown": True}}}})
    assert "repair_hut" not in unknown.events, "Неизвестный аудит не ставит ремонт"
    print("OK: входы участка учитываются в выборе")


def test_gear_fragment():
    graph = Graph.load_merged(
        os.path.join(ROOT, "src", "main", "resources", "data", "theminespirit", "board", "graph.json"),
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
    "more:bad_trip_conditions": "more_avoid_far_trip",
    "more:failures_repeat": "more_failure_backoff",
    "more:station_queue": "more_station_queue",
    "more:drops_expiring": "more_pickup_drops",
    "more:forest_depleted": "more_replant",
    "more:ore_depleted": "more_sustain_mine",
    "more:fence_gap": "more_secure_fence",
    "more:door_open_night": "more_secure_door",
    "more:hopper_uncovered": "more_secure_hopper",
    "more:reserve_unused": "more_share_reserve",
    "more:work_duplicate": "more_avoid_duplicate",
    "more:strategic_goal": "more_strategic_goal",
}


def test_more_nodes():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    for signal, way in MORE.items():
        path = graph.way(way)
        assert path is not None, "Python-модель не видит путь области more: " + way
        assert path.makes in graph.items, "Python-модель не видит узел области more: " + path.makes
        assert graph.items[path.makes].base > 0, "Стартовый вес узла more положителен: " + path.makes
        assert way in graph.unimplemented, "Действие области more пока заглушено: " + way
        assert path.world == signal, "Вход области more — метка мира " + signal
    print("OK: узлы и заглушки области more читаются из фрагментов")


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
    print("OK: входы области more учитываются в выборе")


def test_more2_nodes():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    for way in ("more2_sow_food", "more2_breed_food", "more2_unload_carry", "more2_load_carry",
                "more2_batch_carry", "more2_put_out_fire", "more2_remove_lava", "more2_block_flow",
                "more2_pen_animal", "more2_lure_wild", "more2_guard_villager", "more2_trade_villager",
                "more2_prepare_stage", "more2_make_bed", "more2_build_trail"):
        assert graph.way(way) is not None, "Python-модель не видит новый путь области graphmore2: " + way
        assert not graph.implemented(way), "Действие области graphmore2 пока заглушено: " + way
    for item in ("more2:food_plan", "more2:carry_room", "more2:carry_batch", "more2:safe",
                 "more2:flow_blocked", "more2:animal_penned", "more2:village_safe",
                 "more2:stage_ready", "more2:bed_ready", "more2:trail"):
        assert item in graph.items, "Python-модель не видит новый узел области graphmore2: " + item
    for building in ("more2_field_chest", "more2_trail"):
        assert building in graph.buildings, "Python-модель не видит новую постройку области: " + building
    for event in ("more2_food_low", "more2_inventory_full", "more2_inventory_space", "more2_long_carry",
                  "more2_fire_near_building", "more2_lava_near_building", "more2_hazard_flow",
                  "more2_animal_outside", "more2_wild_herd", "more2_villager_near",
                  "more2_villager_trade", "more2_stage_prep", "more2_bed_needed", "more2_path_needed"):
        assert event in graph.events, "Python-модель не видит новое событие области: " + event
    print("OK: узлы и события области graphmore2 читаются из фрагмента")


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
    assert not fire["implemented"] and not flow["implemented"], "Заглушки области не выдаются как готовые действия"
    print("OK: входы области graphmore2 учитываются в выборе")


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
        test_life_band()
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
        test_conformance()
    except AssertionError as error:
        print(error)
        sys.exit(1)
