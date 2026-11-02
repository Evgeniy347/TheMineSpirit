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
        test_store_nodes()
        test_conformance()
    except AssertionError as error:
        print(error)
        sys.exit(1)
