"""Эволюционный поиск весов графа поселения на видеокарте.

Параметры — КАТЕГОРИИ, а не каждый путь (каждый множитель в пределах ±50 %, 0.5…1.5):
  * базы предметов по виду: perSpirit или этап,
  * базы построек по этапу,
  * веса путей по BoardCase,
  * decay, urgentBoost, buildReadyBoost.

Метрика — сумма шагов до конца этапа 3 по стартовым мирам; застрявший мир
получает штраф limit + 500. Уравнение p = r + A·p решается матрично на torch
(CUDA), поколение считается одним пакетом миров.

Запуск:
    python search.py --mode sensitivity --export ../../TestResults/graph-export
    python search.py --mode es --generations 20 --population 24 --seeds 2
Выход: best-weights.json рядом со скриптом (обновляется после каждого поколения).
"""

import argparse
import fnmatch
import json
import math
import os
import sys

import numpy as np

from graph_model import Graph, PLANNED, ABSENT, BUILT
import sim_gpu

HERE = os.path.dirname(os.path.abspath(__file__))

LOW, HIGH = 0.5, 1.5
PENALTY = 500

SCALARS = ("decay", "urgentBoost", "buildReadyBoost")
DIG_WAYS = {"dig_out", "bridge", "pillar", "restore_trace"}
STORE_EVENTS = {"store_minimum", "transfer_pending", "storage_full", "furnace_ready"}
FARM_EVENTS = {"crop_ready", "animal_starving", "orchard_empty"}
MORE3_EVENTS = set(sim_gpu.MORE3_EVENTS)
MORE4_EVENTS = set(sim_gpu.MORE4_EVENTS)
MORE5_EVENTS = set(sim_gpu.MORE5_EVENTS)
MORE6_EVENTS = set(sim_gpu.MORE6_EVENTS)
MORE7_EVENTS = set(sim_gpu.MORE7_EVENTS)
GEAR_EVENTS = {"tool_worn", "armor_worn"}


def item_category(item):
    return "item:perSpirit" if item.per_spirit else "item:stage" + str(item.stage)


def building_category(building):
    return "building:stage" + str(building.stage)


def way_category(way):
    if way.id in DIG_WAYS:
        return "way:dig"
    return "way:" + str(way.board_case or "other")


def searchable(way):
    """auto_*-рецепты пересобирает graphExport: вес 1.0/0.1 задаёт импорт.

    Подобранный поиском множитель пропал бы при следующей выгрузке, поэтому
    такие пути заморожены и в vector_to_weights не попадают.
    """
    return not way.id.startswith("auto_")


def event_category(name):
    """Полосы новых областей: жизнь, склад, ферма, снаряжение, more2/more3, ночь, поход."""
    if name == "night":
        return "event:night"
    if name == "expedition_ready":
        return "event:more7"
    if name.startswith("life_"):
        return "event:life"
    if name in STORE_EVENTS:
        return "event:store"
    if name in FARM_EVENTS:
        return "event:farm"
    if name.startswith("more2_"):
        return "event:more2"
    if name in MORE3_EVENTS:
        return "event:more3"
    if name in MORE4_EVENTS:
        return "event:more4"
    if name in MORE5_EVENTS:
        return "event:more5"
    if name in MORE6_EVENTS:
        return "event:more6"
    if name in MORE7_EVENTS:
        return "event:more7"
    if name.startswith("more4_"):
        return "event:more4"
    if name.startswith("gear_") or name in GEAR_EVENTS:
        return "event:gear"
    return None


def parameters(graph):
    """Список категорий поиска: баз несколько десятков, а не по пути."""
    categories = set(SCALARS)
    categories.update("choice:" + name for name in graph.costs.choice.values)
    categories.update(item_category(item) for item in graph.items.values())
    categories.update(building_category(building) for building in graph.buildings.values())
    categories.update(way_category(way) for way in graph.ways if searchable(way))
    categories.update(category for category in (event_category(name) for name in graph.events)
                      if category is not None)
    scalars = [name for name in SCALARS if name in categories]
    rest = sorted(categories.difference(SCALARS))
    return scalars + rest


def vector_to_weights(graph, vector, params):
    """Вектор множителей категорий -> множители конкретных весов для Graph.scaled."""
    factors = {name: float(value) for name, value in zip(params, vector)}
    weights = {"items": {}, "buildings": {}, "ways": {}, "events": {}, "choice": {}}
    for name in SCALARS:
        weights[name] = factors.get(name, 1.0)
    for name in graph.costs.choice.values:
        weights["choice"][name] = factors.get("choice:" + name, 1.0)
    for item_id, item in graph.items.items():
        weights["items"][item_id] = factors.get(item_category(item), 1.0)
    for building_id, building in graph.buildings.items():
        weights["buildings"][building_id] = factors.get(building_category(building), 1.0)
    for way in graph.ways:
        if not searchable(way):
            continue
        weights["ways"][way.id] = factors.get(way_category(way), 1.0)
    for name in graph.events:
        category = event_category(name)
        if category is not None:
            weights["events"][name] = factors.get(category, 1.0)
    return weights


def group_vector(params, index, factor):
    vector = np.ones(len(params))
    vector[index] = factor
    return vector


def absolute(graph, weights):
    """Множители -> абсолютные значения в формате graph.json."""
    out = {
        "decay": round(graph.decay * weights.get("decay", 1.0), 6),
        "urgentBoost": int(math.floor(graph.urgent_boost * weights.get("urgentBoost", 1.0) + 0.5)),
        "buildReadyBoost": int(math.floor(graph.build_ready_boost * weights.get("buildReadyBoost", 1.0) + 0.5)),
        "items": {},
        "buildings": {},
        "ways": {},
        "events": {},
        "choice": {},
    }
    for item_id, factor in (weights.get("items") or {}).items():
        item = graph.items.get(item_id)
        if item is not None:
            out["items"][item_id] = int(math.floor(item.base * factor + 0.5))
    for building_id, factor in (weights.get("buildings") or {}).items():
        building = graph.buildings.get(building_id)
        if building is not None:
            out["buildings"][building_id] = int(math.floor(building.base * factor + 0.5))
    for way_id, factor in (weights.get("ways") or {}).items():
        way = graph.way(way_id)
        if way is not None:
            out["ways"][way_id] = round(way.weight * factor, 9)
    for event_name, factor in (weights.get("events") or {}).items():
        event = graph.events.get(event_name)
        if event is None:
            continue
        tasks = {}
        for task in event["tasks"]:
            tasks[task.get("id", "")] = int(math.floor(float(task.get("base", 0)) * factor + 0.5))
        out["events"][event_name] = tasks
    for name, factor in (weights.get("choice") or {}).items():
        if name in graph.costs.choice.values:
            out["choice"][name] = round(graph.costs.choice.values[name] * factor, 6)
    return out


def make_world(graph, stage=3, built_every_other=False, spirits=1, tier=2, stock=None):
    world = sim_gpu.World.settled(graph)
    world.stage = stage
    for index, building in enumerate(graph.buildings.values()):
        if building.stage < stage:
            world.buildings[building.id] = BUILT
        elif building.stage == stage:
            world.buildings[building.id] = BUILT if built_every_other and index % 2 == 0 else PLANNED
        else:
            world.buildings[building.id] = ABSENT
    for spirit in range(1, spirits + 1):
        world.tools("spirit-" + str(spirit), tier if spirit % 2 else -1)
    world.stock.update(stock or {
        "oak_log": 100, "oak_planks": 100, "stick": 100, "cobblestone": 100,
        "coal": 40, "iron_ingot": 20, "dirt": 64, "torch": 30,
    })
    return world


def java_second(graph):
    """GraphMutations.baselines()[3]: этап 2, все постройки в плане."""
    world = sim_gpu.World.settled(graph)
    world.stage = 2
    for building_id in graph.buildings:
        world.buildings[building_id] = PLANNED
    return world


def start_builders():
    """Стартовые миры поиска и проверки: (имя, функция graph -> World)."""
    return [
        ("late3", lambda graph: make_world(graph, 3)),
        ("late3-half", lambda graph: make_world(graph, 3, built_every_other=True)),
        ("stage2-full", java_second),
        ("stage2-all", lambda graph: make_world(graph, 2)),
        ("block-no-torch", lambda graph: sim_gpu.failure_worlds(graph)[0]),
    ] + sim_gpu.input_builders()


def evaluate_population(base_graph, vectors, builders, params, limit=4000, device="cuda"):
    """Пакетная оценка популяции: все кандидаты × все старты одним прогоном."""
    worlds = []
    labels = []
    for index, vector in enumerate(vectors):
        graph = base_graph.scaled(vector_to_weights(base_graph, vector, params))
        for name, builder in builders:
            worlds.append(builder(graph))
            labels.append((index, name))
    outcomes = sim_gpu.run_many(worlds, limit, device)
    scores = [0] * len(vectors)
    detail = [[] for _ in vectors]
    for (index, name), outcome in zip(labels, outcomes):
        value = outcome["steps"] if outcome["finished"] else limit + PENALTY
        if name.startswith("block"):
            if outcome.get("repeat_blocked", 0) > 0:
                value += PENALTY * outcome["repeat_blocked"]
            if not outcome.get("key_raised", False):
                value += PENALTY
            if outcome.get("other_work", 0) == 0:
                value += PENALTY
        scores[index] += value
        detail[index].append({
            "start": name,
            "steps": outcome["steps"],
            "finished": outcome["finished"],
            "stage": outcome["stage"],
            "repeat_blocked": outcome.get("repeat_blocked", 0),
            "key_raised": outcome.get("key_raised", False),
            "other_work": outcome.get("other_work", 0),
        })
    return scores, detail


def evaluate(base_graph, vector, builders, params, limit=4000, device="cuda"):
    scores, detail = evaluate_population(base_graph, [vector], builders, params, limit, device)
    return scores[0], detail[0]


def sensitivity(base_graph, builders, params, limit, device):
    base = np.ones(len(params))
    vectors = [base]
    names = ["base"]
    for index, group in enumerate(params):
        for direction, factor in (("+", 1.3), ("-", 0.7)):
            vectors.append(group_vector(params, index, factor))
            names.append(group + direction)
    scores, detail = evaluate_population(base_graph, vectors, builders, params, limit, device)
    rows = sorted(zip(scores, names, detail))
    print("Сумма шагов (меньше — лучше):")
    for score, name, items in rows:
        steps = ", ".join(item["start"] + "=" + str(item["steps"]) for item in items)
        print(f"  {name:22s} {score:6d}  [{steps}]")
    return rows


def save(path, base_graph, best, params, limit, device):
    weights = vector_to_weights(base_graph, best["vector"], params)
    payload = {
        "note": "Множители в пределах ±50 %; в graph.json не внесены. "
                "absolute — готовые значения для переноса вручную.",
        "score": best["normalized"],
        "seed": best["seed"],
        "generation": best["generation"],
        "parameters": {name: round(float(value), 6)
                       for name, value in zip(params, best["vector"])},
        "weights": weights,
        "absolute": absolute(base_graph, weights),
    }
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)


def load_vector(path, params):
    with open(path, "r", encoding="utf-8") as handle:
        saved = json.load(handle).get("parameters", {})
    vector = np.ones(len(params))
    for index, name in enumerate(params):
        value = saved.get(name)
        if value is not None:
            vector[index] = float(value)
    return np.clip(vector, LOW, HIGH)


def pin(vector, params, fixed):
    for index, name in enumerate(params):
        if any(fnmatch.fnmatchcase(name, pattern) for pattern in fixed):
            vector[index] = 1.0
    return vector


def evolution(base_graph, builders, params, generations, population, seeds, limit, device,
              output, initial=None, sigma0=0.15, fixed=()):
    """Поколения по кругу берут разные старты; отбор — по шагам на этом старте."""
    dimension = len(params)
    mu = max(2, population // 3)
    base = np.ones(dimension) if initial is None else np.clip(initial, LOW, HIGH)
    pin(base, params, fixed)
    base_detail = evaluate_population(base_graph, [base], builders, params, limit, device)[1][0]
    base_by_name = {item["start"]: (item["steps"] if item["finished"] else limit + PENALTY)
                    for item in base_detail}
    best = None
    for seed in seeds:
        rng = np.random.RandomState(seed)
        mean = base.copy()
        sigma = sigma0
        for generation in range(generations):
            rotating = builders[1 + (seed + generation) % (len(builders) - 1)]
            builder = [builders[0], rotating]
            focus = next((item for item in builders if item[0] == "live1"), None)
            if focus is not None and focus[0] != rotating[0] and focus[0] != builders[0][0]:
                builder.append(focus)
            vectors = [pin(mean.copy(), params, fixed)]
            for _ in range(population - 1):
                vectors.append(pin(np.clip(mean + sigma * rng.randn(dimension), LOW, HIGH), params, fixed))
            scores, detail = evaluate_population(base_graph, vectors, builder, params, limit, device)
            order = np.argsort(scores)
            mean = pin(np.mean([vectors[index] for index in order[:mu]], axis=0), params, fixed)
            baseline = sum(base_by_name[name] for name, _ in builder)
            normalized = scores[order[0]] / max(1, baseline)
            previous = best["normalized"] if best is not None else float("inf")
            if normalized < previous:
                best = {
                    "normalized": normalized,
                    "vector": vectors[order[0]].copy(),
                    "seed": seed,
                    "generation": generation,
                    "detail": detail[order[0]],
                }
                save(output, base_graph, best, params, limit, device)
            print(f"  сид {seed} поколение {generation:2d} ({builder[0][0]}): "
                  f"лучший {scores[order[0]]:6d} средний {int(np.mean(scores)):6d} "
                  f"доля {normalized:.4f} sigma {sigma:.3f} лучшая {best['normalized']:.4f}",
                  flush=True)
            sigma = min(0.3, sigma * 1.15) if normalized <= previous else max(0.02, sigma * 0.85)
    return best


def report(base_graph, builders, params, best, limit, device, output):
    base = np.ones(len(params))
    base_score, base_detail = evaluate(base_graph, base, builders, params, limit, device)
    best_score, best_detail = evaluate(base_graph, best["vector"], builders, params, limit, device)
    with open(output, "r", encoding="utf-8") as handle:
        payload = json.load(handle)
    payload["score_before"] = base_score
    payload["score_after"] = best_score
    payload["starts_before"] = base_detail
    payload["starts_after"] = best_detail
    with open(output, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
    print("Старты (шаги, до -> после):")
    for before, after in zip(base_detail, best_detail):
        print(f"  {before['start']:12s} {before['steps']:5d} -> {after['steps']:5d} "
              f"(этап {after['stage']}, {'готово' if after['finished'] else 'застрял'})")
    print(f"Сумма: {base_score} -> {best_score}")
    return payload


def main(argv=None):
    parser = argparse.ArgumentParser(description="Поиск весов графа на видеокарте")
    parser.add_argument("--export", default=os.path.join(os.path.dirname(HERE), "..", "TestResults", "graph-export"))
    parser.add_argument("--mode", choices=("sensitivity", "es"), default="es")
    parser.add_argument("--generations", type=int, default=20)
    parser.add_argument("--population", type=int, default=24)
    parser.add_argument("--seeds", type=int, default=2)
    parser.add_argument("--limit", type=int, default=4000)
    parser.add_argument("--device", default="cuda")
    parser.add_argument("--output", default=os.path.join(HERE, "best-weights.json"))
    parser.add_argument("--init", default="", help="best-weights.json для дообучения")
    parser.add_argument("--fixed", default="", help="категории, которые не трогать (через запятую)")
    args = parser.parse_args(argv)
    base_graph = sim_gpu.load_graph(os.path.abspath(args.export))
    builders = start_builders()
    params = parameters(base_graph)
    if args.mode == "sensitivity":
        sensitivity(base_graph, builders, params, args.limit, args.device)
        return 0
    initial = load_vector(args.init, params) if args.init else None
    fixed = {name.strip() for name in args.fixed.split(",") if name.strip()}
    best = evolution(base_graph, builders, params, args.generations, args.population,
                     list(range(1, args.seeds + 1)), args.limit, args.device, args.output,
                     initial=initial, sigma0=0.08 if initial is not None else 0.15, fixed=fixed)
    report(base_graph, builders, params, best, args.limit, args.device, args.output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
