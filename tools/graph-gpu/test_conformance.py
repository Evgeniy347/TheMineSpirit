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


def operational_ids(graph, facts):
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


def test_operational_inputs():
    graph = Graph.load_merged(os.path.join(EXPORT, "graph.json"), RECIPES, FRAGMENTS)
    store = operational_ids(graph, {"stageReqs": {"minimum": {"oak_log": 64}, "actual": {"oak_log": 0}}})
    assert "store:minimum" in store, "недобор минимума склада обязан дать узел store:minimum"
    dig = operational_ids(graph, {"spirits": {"spirit-1": {"trapped": True, "trap": "sealed"}}})
    assert "dig_out" in dig and "restore_trace" in dig, "дух без пути обязан дать dig_out и restore_trace"
    smelt = operational_ids(graph, {"stations": {"furnace": {
        "id": "furnace", "type": "furnace", "busy": False, "fuel": "coal", "input": "iron_ore", "output": None}}})
    assert "station:smelt:load" in smelt, "загруженная печь обязана дать путь загрузки"
    space = operational_ids(graph, {"chests": [{
        "position": {"x": 1, "y": 2, "z": 3}, "contents": {}, "freeSlots": 1, "totalSlots": 27}]})
    assert "storage_space" in space, "мало места обязано дать узел новой пары сундуков"
    print("OK: новые входы склада и копания дают узлы графа")


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
        test_operational_inputs()
        test_conformance()
    except AssertionError as error:
        print(error)
        sys.exit(1)
