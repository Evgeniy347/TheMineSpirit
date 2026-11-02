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
        test_area_nodes()
        test_world_fragment()
        test_world_inputs()
        test_conformance()
    except AssertionError as error:
        print(error)
        sys.exit(1)
