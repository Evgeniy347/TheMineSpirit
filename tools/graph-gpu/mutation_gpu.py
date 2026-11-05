"""Перебор мутаций графа на видеокарте с проверкой выборки Java.

Сверка «Python = Java»: TestResults/mutation/simulation.json пишет Java (SimulationSample),
здесь те же миры считаются моделью sim_gpu и исход сравнивается по шагам, этапу и финишу.
Основной перебор — пачка миров с мутациями одним вызовом run_many (CUDA).

Запуск:
    PYTHONIOENCODING=utf-8 py -3.9 tools/graph-gpu/mutation_gpu.py --sample 10
Бюджет: полный перебор не нужен (решение владельца 05.11), выборка с ротацией сида.
"""

import argparse
import json
import os
import random
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)

import sim_gpu
from graph_model import BUILT, PLANNED, ABSENT

SAMPLE = os.path.join(ROOT, "TestResults", "mutation", "simulation.json")
ENV_SEED = "MUTATION_SEED"


def seed():
    value = os.environ.get(ENV_SEED)
    if value and value.strip():
        return int(value.strip())
    import datetime
    days = (datetime.datetime.now(datetime.timezone.utc).date()
            - datetime.date(1970, 1, 1)).days
    return days * 7919 + 17


def spirit_key(name):
    tail = name.rsplit("-", 1)[-1]
    return int(tail) if tail.isdigit() else 0


def world_from_state(graph, data):
    kits = dict(sorted((data.get("kits") or {}).items(), key=lambda entry: spirit_key(entry[0])))
    return sim_gpu.World(
        graph,
        stock={k: int(v) for k, v in (data.get("stock") or {}).items()},
        kits={k: dict(v) for k, v in kits.items()},
        buildings=dict(data.get("buildings") or {}),
        stage=int(data.get("stage", 1)),
        storage_free=int(data.get("storageFreePercent", 0)),
        events=set(data.get("events") or []),
        world=set(data.get("world") or []),
        materials={k: dict(v) for k, v in (data.get("materials") or {}).items()},
        stations={k: int(v) for k, v in (data.get("stations") or {}).items()},
        distances={k: int(v) for k, v in (data.get("distances") or {}).items()},
        facts=data.get("facts") or {})


def with_item(world, item, count):
    copy = world.copy()
    copy.stock[item] = count
    return copy


def with_tools(world, tier):
    return world.copy().tools("spirit-1", tier)


def with_building(world, building, state):
    copy = world.copy()
    copy.buildings[building] = state
    return copy


def with_event(world, event):
    return world.copy().event(event)


def without_world(world, resource):
    copy = world.copy()
    copy.world.discard(resource)
    return copy


def with_free(world, free):
    copy = world.copy()
    copy.storage_free = free
    return copy


def mutations(graph):
    """Правила GraphMutations.single(): тот же набор, имена для лога."""
    all_mutations = []
    for item in graph.items.values():
        counts = [0, max(0, item.xmin - 1), item.xmin * 4, 10000]
        for count in counts:
            all_mutations.append((f"{item.id}={count}",
                                  lambda world, item_id=item.id, value=count: with_item(world, item_id, value)))
    for tier in (-1, 0, 1, 2):
        all_mutations.append((f"tools={tier}",
                              lambda world, value=tier: with_tools(world, value)))
    for building in graph.buildings:
        for state in (BUILT, PLANNED, ABSENT):
            all_mutations.append((f"{building}={state}",
                                  lambda world, key=building, value=state: with_building(world, key, value)))
    for event in graph.events:
        all_mutations.append((f"event={event}",
                              lambda world, name=event: with_event(world, name)))
    seen = set()
    for way in graph.ways:
        if way.world and way.world not in seen:
            seen.add(way.world)
            all_mutations.append((f"нет {way.world}",
                                  lambda world, resource=way.world: without_world(world, resource)))
    for sapling in graph.members("#saplings"):
        all_mutations.append((f"{sapling}=2",
                              lambda world, item=sapling: with_item(world, item, 2)))
    for free in (0, 19, 20, 100):
        all_mutations.append((f"free={free}",
                              lambda world, value=free: with_free(world, value)))
    return all_mutations


def simulation_pool(graph):
    return [mutation for mutation in mutations(graph) if not mutation[0].startswith("нет ")]


def compare_java(graph, path, device):
    with open(path, encoding="utf-8") as handle:
        cases = json.load(handle)
    problems = []
    by_limit = {}
    for case in cases:
        by_limit.setdefault(int(case["limit"]), []).append(case)
    for limit, group in by_limit.items():
        outcomes = sim_gpu.run_many(
            [world_from_state(graph, case["state"]) for case in group], limit, device)
        for case, outcome in zip(group, outcomes):
            if bool(outcome["finished"]) != bool(case["finished"]):
                problems.append(f"{case['name']}: финиш Python {outcome['finished']} != Java {case['finished']}")
            elif outcome["steps"] != case["steps"]:
                problems.append(f"{case['name']}: шагов Python {outcome['steps']} != Java {case['steps']}")
            elif outcome["stage"] != case["stage"]:
                problems.append(f"{case['name']}: этап Python {outcome['stage']} != Java {case['stage']}")
    return cases, problems


def bulk(graph, count, limit, device, rotation):
    bases = sim_gpu.baselines(graph)[1:4]
    pool = simulation_pool(graph)
    random.Random(rotation).shuffle(pool)
    chosen = pool[:min(count, len(pool))]
    worlds = []
    names = []
    for name, apply in chosen:
        for base in bases:
            worlds.append(apply(base))
            names.append(f"{name}, старт этап {base.stage}")
    started = time.time()
    outcomes = sim_gpu.run_many(worlds, limit, device)
    elapsed = time.time() - started
    stuck = [f"{names[index]}: {outcome['stuck']}"
             for index, outcome in enumerate(outcomes) if not outcome["finished"]]
    return len(worlds), elapsed, stuck


def main(argv=None):
    parser = argparse.ArgumentParser(description="Перебор мутаций графа на видеокарте")
    parser.add_argument("--sample", type=int, default=10, help="мутаций в пачке (каждая на 3 стартах)")
    parser.add_argument("--limit", type=int, default=8000)
    parser.add_argument("--device", default="cuda")
    parser.add_argument("--export", default=os.path.join(ROOT, "TestResults", "graph-export"))
    parser.add_argument("--seed", type=int, default=None)
    args = parser.parse_args(argv)
    rotation = args.seed if args.seed is not None else seed()
    graph = sim_gpu.load_graph(args.export)
    print(f"сид мутаций {rotation}; повторить: MUTATION_SEED={rotation}", flush=True)
    cases, problems = compare_java(graph, SAMPLE, args.device)
    print(f"сверка с Java: {len(cases)} миров, расхождений {len(problems)}", flush=True)
    for problem in problems[:10]:
        print("  " + problem, flush=True)
    count, elapsed, stuck = bulk(graph, args.sample, args.limit, args.device, rotation)
    print(f"GPU-пачка: миров {count}, застряло {len(stuck)}, время {elapsed:.1f} с", flush=True)
    for line in stuck[:10]:
        print("  " + line, flush=True)
    if problems or stuck:
        return 1
    print("OK: Python = Java на выборке; пачка мутаций прошла этап 3", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
