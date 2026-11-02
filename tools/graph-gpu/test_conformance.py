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
            json.dump({"items": {"rope": {"xmin": 0, "base": 1}}}, handle)
        graph = Graph.load_merged(manual, recipes, fragments)
        assert "rope" in graph.items, "Python-модель обязана читать фрагменты графа"
        with open(os.path.join(fragments, "conflict.json"), "w", encoding="utf-8") as handle:
            json.dump({"items": {"stick": {"xmin": 0, "base": 1}}}, handle)
        try:
            Graph.load_merged(manual, recipes, fragments)
        except ValueError as error:
            assert "conflict.json" in str(error), "Ошибка конфликта обязана назвать файл: " + str(error)
        else:
            raise AssertionError("Повтор id во фрагменте обязан отклоняться")
    print("OK: фрагменты графа сливаются, конфликт назван путём")


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


def test_store_fragment():
    graph = Graph.load_merged(
        os.path.join(ROOT, "src", "main", "resources", "data", "theminespirit", "board", "graph.json"),
        RECIPES,
        FRAGMENTS)
    for node in ("store_minimum", "store_transfer", "store_smelt_put", "store_smelt_take",
                 "store_storage_space", "store_sort"):
        assert node in graph.events, "Python-модель обязана читать складской узел '" + node + "'"
        task = graph.events[node]["tasks"][0]
        assert task.get("implemented") is False, "Складской узел пока заглушка: " + node
    print("OK: складские узлы читаются из фрагмента")


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


def test_store_inputs():
    state = State({
        "stage": 1,
        "kits": {"spirit-1": {}},
        "facts": {
            "stageReqs": {"minimum": {"cobblestone": 160}, "actual": {"cobblestone": 10}},
            "chests": [{"position": {"x": 0, "y": 64, "z": 0},
                        "contents": {"minecraft:cobblestone": 10}, "freeSlots": 4, "totalSlots": 27}],
            "spirits": {"spirit-1": {"inventory": {"minecraft:oak_log": 12}}},
            "stations": {"furnace": {"id": "furnace", "type": "furnace", "input": None,
                                     "output": "output", "fuel": "fuel"}},
        },
    })
    for node in ("store_minimum", "store_transfer", "store_smelt_take"):
        assert node in state.events, "Складской вход обязан добавить узел '" + node + "'"
    print("OK: складские входы учитываются в выборе")


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


def test_stage_minimum():
    graph = Graph.load_merged(
        os.path.join(ROOT, "src", "main", "resources", "data", "theminespirit", "board", "graph.json"),
        RECIPES,
        FRAGMENTS)
    state = State({
        "stage": 1,
        "kits": {"spirit-1": {"pickaxe": 1}},
        "stock": {"cobblestone": 100},
        "facts": {"stageReqs": {"minimum": {"cobblestone": 160}, "actual": {"cobblestone": 100}}},
    })
    tasks = {task["id"] for task in plan(graph, state)["tasks"]}
    assert "mine_cobble" in tasks, "Минимум склада выше обычного порога обязан поднять добычу камня"
    print("OK: минимум склада учитывается в выборе")


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
        test_world_fragment()
        test_world_inputs()
        test_store_fragment()
        test_gear_fragment()
        test_store_inputs()
        test_gear_inputs()
        test_stage_minimum()
        test_conformance()
    except AssertionError as error:
        print(error)
        sys.exit(1)
