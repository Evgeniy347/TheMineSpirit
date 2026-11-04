"""Сверка Python-модели с Java на выгрузке conformance.json.

Запуск: python test_conformance.py
Выгрузка: ./gradlew graphExport -> TestResults/graph-export/{graph,conformance}.json

Критерий: набор заданий и feasible совпадают точно, priority расходится не больше чем на 1.
"""

import json
import os
import sys
import tempfile

import search
import sim_gpu
from graph_model import Graph, Planner, State, plan

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
        if way in MORE_READY:
            assert graph.implemented(way), "Действие области more связано с исполнением: " + way
        else:
            assert way in graph.unimplemented, "Сигнал управления more остаётся без отдельного действия: " + way
        assert path.world == signal, "Вход области more — метка мира " + signal
    print("OK: узлы области more и их исполнение читаются из фрагментов")


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


MORE2_READY = {
    "more2_sow_food", "more2_breed_food", "more2_unload_carry", "more2_put_out_fire",
    "more2_remove_lava", "more2_block_flow", "more2_pen_animal", "more2_lure_wild",
    "more2_guard_villager", "more2_prepare_stage", "more2_make_bed", "more2_build_trail",
}


def test_more2_nodes():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    for way in ("more2_sow_food", "more2_breed_food", "more2_unload_carry", "more2_load_carry",
                "more2_batch_carry", "more2_put_out_fire", "more2_remove_lava", "more2_block_flow",
                "more2_pen_animal", "more2_lure_wild", "more2_guard_villager", "more2_trade_villager",
                "more2_prepare_stage", "more2_make_bed", "more2_build_trail"):
        assert graph.way(way) is not None, "Python-модель не видит новый путь области graphmore2: " + way
        if way in MORE2_READY:
            assert graph.implemented(way), "Действие области graphmore2 связано с исполнением: " + way
        else:
            assert not graph.implemented(way), "Сигнал логистики/торговли остаётся без отдельного действия: " + way
    for item in ("more2:food_plan", "more2:carry_room", "more2:carry_batch", "more2:safe",
                 "more2:flow_blocked", "more2:animal_penned", "more2:village_safe",
                 "more2:stage_ready", "more2:bed_ready", "more2:trail"):
        assert item in graph.items, "Python-модель не видит новый узел области graphmore2: " + item
    for building in ("more2_field_chest", "more2_trail"):
        assert building in graph.buildings, "Python-модель не видит новую постройку области: " + building
        assert graph.implemented(building), "Постройка области graphmore2 связана с исполнением: " + building
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
    assert fire["implemented"] and flow["implemented"], "Тушение огня и заливка потока связаны с исполнением"
    print("OK: входы области graphmore2 учитываются в выборе")




def test_more3_nodes():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    events = ("danger_spot", "spirit_death_drop", "work_yield_low", "trash_found",
              "chest_purpose_missing", "work_conflict", "station_idle", "player_sleep",
              "sand_collapse", "gravel_collapse", "farmland_trampled", "world_decay",
              "stage_slow")
    ways = ("secure_danger", "recover_death_drop", "retune_work_cost", "clear_trash",
            "assign_chest_purpose", "spread_work", "use_idle_station", "home_night_work",
            "repair_world_decay", "raise_bottleneck_weight")
    for event in events:
        assert event in graph.events, "Python-модель не видит событие области more3: " + event
    for way in ways:
        assert graph.way(way) is not None, "Python-модель не видит путь области more3: " + way
    assert "#trash" in graph.groups, "Python-модель не видит группу мусора"
    tasks = {task["id"] for task in plan(graph, State({
        "stage": 1, "kits": {"spirit-1": {}}, "events": list(events)}))["tasks"]}
    for event in events:
        assert any(task.startswith("event:" + event + ":") for task in tasks),             "Python: событие области more3 не даёт задания: " + event
    print("OK: узлы и события области more3 учитываются в выборе")


MORE6_EVENTS = (
    "more6_spirits_short", "more6_weak_far", "more6_build_unfinished",
    "more6_carry_materials", "more6_station_broken", "more6_loose_items",
    "more6_water_needed", "more6_lava_needed", "more6_repeat_demand",
    "more6_spirit_idle",
)

MORE6_WAYS = (
    "more6_call_spirit", "more6_return_weak", "more6_send_near",
    "more6_finish_build", "more6_deposit_materials", "more6_refuel_station",
    "more6_place_station", "more6_pickup_items", "more6_fetch_water",
    "more6_fetch_lava", "more6_maintain_stock", "more6_assign_idle",
)
MORE6_IMPLEMENTED = {
    "more6_deposit_materials", "more6_refuel_station", "more6_place_station",
    "more6_pickup_items", "more6_maintain_stock", "more6_assign_idle",
}


def test_more6_nodes():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    for event in MORE6_EVENTS:
        assert event in graph.events, "Python-модель не видит событие области more6: " + event
        assert graph.events[event]["tasks"], "Событие области more6 ставит работу: " + event
    for way in MORE6_WAYS:
        path = graph.way(way)
        assert path is not None, "Python-модель не видит путь области more6: " + way
        assert path.makes in graph.items, "Python-модель не видит узел области more6: " + path.makes
        assert graph.items[path.makes].base > 0, "Стартовый вес узла more6 положителен: " + path.makes
        assert graph.implemented(way) == (way in MORE6_IMPLEMENTED),             "Путь области more6 либо связан с действием, либо честно помечен заглушкой: " + way
    print("OK: узлы области more6 читаются из фрагментов, заглушки отмечены")


def test_more6_inputs():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    tasks = {task["id"] for task in plan(graph, State({
        "stage": 1, "kits": {"spirit-1": {}}, "events": list(MORE6_EVENTS)}))["tasks"]}
    for event in MORE6_EVENTS:
        assert any(task.startswith("event:" + event + ":") for task in tasks), \
            "Python: событие области more6 не даёт задания: " + event
    quiet = {task["id"] for task in plan(graph, State({"stage": 1, "kits": {"spirit-1": {}}}))["tasks"]}
    for event in MORE6_EVENTS:
        assert not any(task.startswith("event:" + event + ":") for task in quiet), \
            "Python: без входа области more6 задачи нет: " + event
    print("OK: входы области more6 учитываются в выборе")


MORE5_EVENTS = ("more5_player_weak", "more5_crops_dark", "more5_saplings_dark",
                "more5_crops_dry", "more5_growth_unfed", "more5_fuel_low",
                "more5_build_shortage", "more5_mob_broken", "more5_route_blocked",
                "more5_chest_far", "more5_mine_dark", "more5_wood_drops")
MORE5_WAYS = ("more5_defend_player", "more5_feed_player", "more5_heal_player",
              "more5_light_crops", "more5_light_saplings", "more5_water_crops",
              "more5_fertilize_growth", "more5_gather_fuel", "more5_make_charcoal",
              "more5_gather_build_material", "more5_craft_build_material",
              "more5_restore_block", "more5_bridge_route", "more5_detour_route",
              "more5_place_site_chest", "more5_carry_to_site", "more5_light_mine",
              "more5_craft_mine_torch", "more5_pickup_wood_drop", "more5_plant_sapling")
MORE5_IMPLEMENTED = {
    "more5_defend_player", "more5_feed_player", "more5_light_crops",
    "more5_light_saplings", "more5_fertilize_growth", "more5_gather_fuel",
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
    for item in ("more5:player_guard", "more5:growth_lit", "more5:fuel_ready",
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
    for task_id in ("event:more5_player_weak:more5_defend_player",
                    "event:more5_crops_dark:more5_light_crops",
                    "event:more5_wood_drops:more5_pickup_wood_drop"):
        assert task_id in tasks, "Python: нет ожидаемого задания области graphmore5: " + task_id
        assert tasks[task_id]["implemented"], "Связанное действие области выдаётся готовым: " + task_id
    stub = "event:more5_crops_dry:more5_water_crops"
    assert stub in tasks and not tasks[stub]["implemented"],         "Бессмысленная заглушка области не выдаётся готовой: " + stub
    assert tasks["event:more5_player_weak:more5_defend_player"]["priority"] > 100, \
        "Python: защита игрока в бою выше обычного потолка"
    print("OK: входы области graphmore5 учитываются в выборе")


MORE4_EVENTS = (
    "more4_player_near", "more4_player_hungry", "more4_player_tool_broken",
    "more4_wrong_tool_hand", "more4_portal_found", "more4_portal_unsafe",
    "more4_space_found", "more4_space_tight", "more4_tamed_pet", "more4_rare_found",
    "more4_rare_mixed", "more4_caution",
)
MORE4_WAYS = (
    "more4_guard_settlement", "more4_watch_chests", "more4_bring_player_food",
    "more4_lend_player_tool", "more4_switch_tool", "more4_guard_portal",
    "more4_fence_portal", "more4_plan_expansion", "more4_find_space",
    "more4_guard_with_pet", "more4_leave_pet_alone", "more4_store_rare",
    "more4_spare_rare", "more4_sort_rare", "more4_feed_hungry", "more4_rest_injured",
)


def test_more4_nodes():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    for event in MORE4_EVENTS:
        assert event in graph.events, "Python-модель не видит событие области more4: " + event
    for way in MORE4_WAYS:
        assert graph.way(way) is not None, "Python-модель не видит путь области more4: " + way
        assert not graph.implemented(way), "Действие области graphmore4 пока заглушено: " + way
    for item in ("more4:settlement_guarded", "more4:player_helped", "more4:tool_ready",
                 "more4:portal_guarded", "more4:expansion_planned", "more4:pet_guarded",
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
    "more7:work_split": "more7_spread_work",
    "more7:stage_gated": "more7_defer_gated",
    "more7:harvest_peak": "more7_harvest_early",
    "more7:return_peak": "more7_return_home",
    "more7:pen_room": "more7_expand_pen",
    "more7:field_room": "more7_expand_field",
    "more7:order_delivered": "more7_order_to_player",
}


def test_more7_nodes():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    events = ("more7_work_pileup", "more7_gated_item", "more7_morning_harvest",
              "more7_evening_return", "more7_pen_full", "more7_field_full",
              "more7_order_ready")
    ways = ("more7_spread_work", "more7_defer_gated", "more7_harvest_early",
            "more7_return_home", "more7_expand_pen", "more7_hold_herd",
            "more7_expand_field", "more7_hold_sowing", "more7_order_to_player",
            "more7_order_to_chest")
    for event in events:
        assert event in graph.events, "Python-модель не видит событие области graphmore7: " + event
    implemented = {"more7_harvest_early", "more7_expand_pen", "more7_return_home",
                   "more7_order_to_chest"}
    for way in ways:
        assert graph.way(way) is not None, "Python-модель не видит путь области graphmore7: " + way
        assert graph.implemented(way) == (way in implemented),             "Путь области graphmore7 либо связан с действием, либо честно помечен заглушкой: " + way
    for item, way in MORE7.items():
        assert item in graph.items, "Python-модель не видит узел области graphmore7: " + item
        assert graph.items[item].base > 0, "Стартовый вес узла more7 положителен: " + item
        assert graph.way(way) is not None, "Путь узла more7 объявлен: " + way
    print("OK: узлы и события области graphmore7 читаются из фрагмента")


def test_more7_inputs():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    tasks = {task["id"] for task in plan(graph, State({
        "stage": 1, "kits": {"spirit-1": {}}, "events": [
            "more7_work_pileup", "more7_gated_item", "more7_morning_harvest",
            "more7_evening_return", "more7_pen_full", "more7_field_full",
            "more7_order_ready"]}))["tasks"]}
    for event in ("more7_work_pileup", "more7_gated_item", "more7_morning_harvest",
                  "more7_evening_return", "more7_pen_full", "more7_field_full",
                  "more7_order_ready"):
        assert any(task.startswith("event:" + event + ":") for task in tasks), \
            "Python: событие области graphmore7 не даёт задания: " + event
    quiet = {task["id"] for task in plan(graph, State({
        "stage": 1, "kits": {"spirit-1": {}}, "events": []}))["tasks"]}
    for way in MORE7.values():
        assert way not in quiet, "Python: без входа области graphmore7 задачи нет: " + way
    print("OK: входы области graphmore7 учитываются в выборе")


def test_baseline_trail():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    outcome = sim_gpu.run(sim_gpu.baselines(graph)[0], limit=4000)
    assert outcome["steps"] == 84 and outcome["ticks"] == 59220, \
        "Пустой мир обязан повторить Java (84 шага, 59 220 тиков), а не решение линейной системы: " \
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
                "band": 20,
                "tasks": [
                    {"id": "defend", "case": "ACTIVITY_DEFEND", "base": 120, "consumes": {}},
                    {"id": "sleep", "case": "ACTIVITY_REST", "base": 101, "consumes": {}},
                ],
            }
        },
    })
    scaled = graph.scaled({"events": {"life_threat": 0.5}})
    assert scaled.events["life_threat"]["tasks"][0]["base"] == 110, \
        "Полоса жизни масштабируется выше maxPriority: 100 + (120 - 100) / 2"
    assert scaled.events["life_threat"]["tasks"][1]["base"] == 101, \
        "Полоса жизни не опускается до maxPriority: сон остаётся выше 100"
    assert graph.events["life_threat"]["tasks"][0]["base"] == 120, \
        "Масштабирование не меняет исходный граф"
    print("OK: множители событий меняют стартовые веса полос")


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


        "more6": "event:more6_spirits_short",
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
    for way in ("more5_defend_player", "more5_pickup_wood_drop", "more5_light_mine"):
        assert any(way in task for task in more5),             "Мир more5 обязан вызвать путь '" + way + "': " + str(more5)
    area = [step[1] for step in sim_gpu.run(worlds["world"], limit=1600)["trail"]]
    repairs = {name for name in graph.events if name.startswith("repair_")}
    for name in repairs:
        assert any(task.startswith("event:" + name + ":") for task in area),             "Мир участка обязан вызвать ремонт '" + name + "'"
    for name, expected in (("gear", {"gear_kit_mining", "gear_kit_woodcutting",
                                     "gear_kit_farming", "gear_kit_building"}),
                           ("farm", {"crop_dry", "pen_full"}),
                           ("life", {"life_injury"})):
        events = set(worlds[name].state().events)
        missing = expected - events
        assert not missing, "Вход мира '" + name + "' не подаёт события: " + str(sorted(missing))
    print("OK: мёртвые входы областей вызываются мирами (ферма, ремонт, снаряжение, жизнь)")


def test_stage_two_worlds():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    worlds = [("stage2-full", sim_gpu.baselines(graph)[3]),
              ("stage2-all", search.make_world(graph, 2))]
    for name, world in worlds:
        outcome = sim_gpu.run(world, limit=4000)
        assert outcome["stage"] >= 3,             "Мир " + name + " обязан пройти этап 2 без сторожей: " + outcome["stuck"]
    print("OK: миры этапа 2 доходят до этапа 3 (склад и загоны не держат этап)")


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
        test_more3_nodes()
        test_more6_nodes()
        test_more6_inputs()
        test_more5_nodes()
        test_more5_inputs()
        test_more4_nodes()
        test_more4_inputs()
        test_more7_nodes()
        test_more7_inputs()
        test_baseline_trail()
        test_audit_inputs()
        test_event_scaling()
        test_input_worlds()
        test_input_effects()
        test_dead_inputs()
        test_stage_two_worlds()
        test_conformance()
    except AssertionError as error:
        print(error)
        sys.exit(1)
