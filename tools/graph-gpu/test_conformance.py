"""Сверка Python-модели с Java на выгрузке conformance.json.

Запуск: python test_conformance.py
Выгрузка: ./gradlew graphExport -> TestResults/graph-export/{graph,conformance}.json

Критерий: набор заданий и feasible совпадают точно, priority расходится не больше чем на 1.
"""

import json
import os
import sys

from graph_model import Graph, State, plan

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
EXPORT = os.path.join(ROOT, "TestResults", "graph-export")


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


def test_conformance():
    with open(os.path.join(EXPORT, "graph.json"), "r", encoding="utf-8") as handle:
        graph = Graph(json.load(handle))
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
        test_conformance()
    except AssertionError as error:
        print(error)
        sys.exit(1)
