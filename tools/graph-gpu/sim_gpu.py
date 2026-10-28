"""Пакетная симуляция поселения на torch во времени.

Правила повторяют Java: GraphSimulation + GraphWorld
(src/test/java/com/theminespirit/core/board/graph/): тики заданий из costs, дорога,
износ инструментов, станции, события, этапы 1–3.
Миры идут пакетом, линейная система p = r + A·p решается на видеокарте.

Запуск:
    PYTHONIOENCODING=utf-8 py -3.9 sim_gpu.py --export ../../TestResults/graph-export
Сверка с Java: шаги, тики и порядок заданий по 5 базовым мирам
(TestResults/mutation/graph-trail.md, simulate_Baselines_WriteTrailReport).
"""

import argparse
import heapq
import math
import os
import sys

from graph_model import Graph, Planner, State, BUILT, PLANNED, ABSENT, EPSILON, MAX_ITERATIONS

try:
    import torch
except Exception:
    torch = None

LAST_STAGE = 3
IDLE_TICKS = 100
IDLE_LIMIT = 400
MAX_INT = 2 ** 31 - 1


class World:
    def __init__(self, graph, stock=None, kits=None, buildings=None, stage=1,
                 storage_free=60, events=None, world=None, materials=None,
                 stations=None, distances=None):
        self.graph = graph
        self.stock = dict(stock or {})
        self.kits = {k: dict(v) for k, v in (kits or {}).items()}
        self.buildings = dict(buildings or {})
        self.stage = stage
        self.storage_free = storage_free
        self.events = set(events or ())
        self.world = set(world or ())
        self.materials = {k: dict(v) for k, v in (materials or {}).items()}
        self.stations = dict(stations or {})
        self.distances = dict(distances or {})

    @classmethod
    def empty(cls, graph):
        world = cls(graph)
        world.kits["spirit-1"] = {}
        world.world = {"tree:oak", "tree:jungle", "sand", "clay", "animals",
                       "flower:poppy", "flower:dandelion"}
        for building in graph.buildings.values():
            world.buildings[building.id] = PLANNED if building.stage == 1 and not building.optional else ABSENT
        return world

    @classmethod
    def settled(cls, graph):
        world = cls.empty(graph)
        for building in ("crafting_table", "furnace", "clearing", "chests", "mine"):
            world.buildings[building] = BUILT
        world.tools("spirit-1", 1)
        world.stock.update({"oak_log": 30, "oak_planks": 20, "stick": 10,
                            "cobblestone": 40, "coal": 4})
        return world

    def tools(self, spirit, tier):
        kit = {}
        if tier >= 0:
            for kind in self.graph.kind_names():
                kit[kind] = tier
        self.kits[spirit] = kit
        return self

    def with_item(self, item, count):
        self.stock[item] = count
        return self

    def event(self, name):
        self.events.add(name)
        return self

    def built(self, building, built):
        self.buildings[building] = built
        return self

    def copy(self):
        return World(self.graph, self.stock, self.kits, self.buildings, self.stage,
                     self.storage_free, self.events, self.world, self.materials,
                     self.stations, self.distances)

    def state(self):
        return State({
            "stock": self.stock,
            "stage": self.stage,
            "kits": self.kits,
            "buildings": self.buildings,
            "materials": self.materials,
            "storageFreePercent": self.storage_free,
            "events": sorted(self.events),
            "world": sorted(self.world),
            "distances": self.distances,
            "stations": self.stations,
        })

    def plan(self):
        return Planner(self.graph, self.state()).plan()


def baselines(graph):
    empty = World.empty(graph)
    settled = World.settled(graph)
    crowded = World.settled(graph).tools("spirit-2", -1).tools("spirit-3", 2)
    second = World.settled(graph)
    second.stage = 2
    for building_id in graph.buildings:
        second.buildings[building_id] = PLANNED
    ten = World.settled(graph)
    for index in range(2, 11):
        ten.tools("spirit-" + str(index), -1)
    return [empty, settled, crowded, second, ten]


def unbuilt(world):
    return [b.id for b in world.graph.buildings.values()
            if b.stage <= world.stage and not b.optional and world.buildings.get(b.id) != BUILT]


def promote(world):
    while world.stage <= LAST_STAGE and not unbuilt(world):
        world.stage += 1
        stage = world.stage
        for building in world.graph.buildings.values():
            if building.stage == stage and not building.optional \
                    and world.buildings.get(building.id) != BUILT:
                world.buildings[building.id] = PLANNED


def issue_order(plan):
    tasks = [task for task in plan["tasks"] if task["feasible"]]
    tasks.sort(key=lambda task: (-task["priority"], task["id"]))
    return tasks


def have(world, item):
    if world.graph.is_group(item):
        return sum(max(0, world.stock.get(member, 0)) for member in world.graph.members(item))
    return world.stock.get(item, 0)


def stocked(world, task):
    return all(have(world, item) >= value for item, value in task["consumes"].items())


def consume(world, item, count):
    if not world.graph.is_group(item):
        world.stock[item] = world.stock.get(item, 0) - count
        return
    left = count
    for member in world.graph.members(item):
        take = min(left, max(0, world.stock.get(member, 0)))
        world.stock[member] = world.stock.get(member, 0) - take
        left -= take


def answer(world, plan, strategist=None):
    for notice in plan["notices"]:
        building_id = notice.get("building")
        wanted = world.graph.buildings.get(building_id)
        if wanted is not None and wanted.stage <= world.stage and world.buildings.get(wanted.id) != BUILT:
            world.buildings[wanted.id] = PLANNED
            if strategist is not None:
                strategist.append(str(world.stage) + ":" + wanted.id)


def finish(world, work, wear):
    """GraphSimulation.finish: результат задания в момент его завершения."""
    task = work["task"]
    task_id = task["id"]
    if task_id.startswith("explore:"):
        world.world.add(task["makes"])
        return
    if task_id.startswith("station:"):
        world.stations[task["makes"]] = world.stations.get(task["makes"], 1) + 1
        return
    if task_id.startswith("event:"):
        world.events.discard(task_id.split(":", 2)[1])
        return
    if task_id.startswith("building:"):
        world.buildings[task["makes"]] = BUILT
        if task["makes"] == world.graph.storage_building:
            world.storage_free = max(world.storage_free, 60)
        return
    costs = world.graph.costs
    if world.graph.is_tool(task["makes"]):
        split = task["makes"].find("_")
        kind = task["makes"][split + 1:]
        tier = task["makes"][:split]
        owner = task["spirit"] or work["spirit"]
        world.kits[owner][kind] = max(world.kits[owner].get(kind, -1), world.graph.rank(tier))
        wear[owner + ":" + kind] = costs.durability(tier)
        return
    at = task_id.find("@")
    way = world.graph.way(task_id if at < 0 else task_id[:at])
    if way is None:
        return
    world.stock[task["makes"]] = world.stock.get(task["makes"], 0) + way.yield_
    if way.tool is not None:
        spirit = work["spirit"]
        key = spirit + ":" + way.tool
        rank = world.kits.get(spirit, {}).get(way.tool, -1)
        fresh = MAX_INT if rank < 0 else costs.durability(world.graph.tiers[rank])
        left = wear.get(key, fresh) - costs.uses(way.id, way.yield_)
        if left <= 0 and spirit in world.kits:
            world.kits[spirit].pop(way.tool, None)
            wear.pop(key, None)
        else:
            wear[key] = left


def assign(world, plan, now, free_at, running, seq, log):
    """GraphSimulation.assign: один заказ на свободного духа, время — тиками задания."""
    started = False
    taken = {work["task"]["id"] for _at, _seq, work in running}
    trail, stage_steps, streak = log
    for spirit in list(world.kits):
        if free_at.get(spirit, now) > now:
            continue
        chosen = None
        for task in issue_order(plan):
            if task["id"] in taken:
                continue
            if task["spirit"] is not None and task["spirit"] != spirit:
                continue
            if not stocked(world, task):
                continue
            chosen = task
            break
        if chosen is None:
            free_at[spirit] = now + IDLE_TICKS
            continue
        taken.add(chosen["id"])
        for item, count in chosen["consumes"].items():
            consume(world, item, count)
        finish_at = now + max(1, chosen["ticks"])
        work = {"finish_at": finish_at, "spirit": spirit, "task": chosen}
        heapq.heappush(running, (finish_at, seq[0], work))
        seq[0] += 1
        free_at[spirit] = finish_at
        trail.append((world.stage, chosen["id"], chosen["priority"]))
        stage_steps[world.stage] = stage_steps.get(world.stage, 0) + 1
        streak[chosen["id"]] = streak.get(chosen["id"], 0) + 1
        started = True
    return started


def _solve_flows(flows, device, direct=True):
    """Батчевая матричная система (I − A)·p = r на torch; без torch — по одному."""
    if torch is None or not flows:
        return [flow.solve() for flow in flows]
    if device == "cuda" and not torch.cuda.is_available():
        device = "cpu"
    if direct:
        return _solve_direct(flows, device)
    size = max(len(flow.index) for flow in flows)
    edges = max(1, max(len(flow.links) for flow in flows))
    count = len(flows)
    base = torch.zeros((count, size), dtype=torch.float64, device=device)
    source = torch.zeros((count, edges), dtype=torch.long, device=device)
    target = torch.zeros((count, edges), dtype=torch.long, device=device)
    factor = torch.zeros((count, edges), dtype=torch.float64, device=device)
    for row, flow in enumerate(flows):
        for key, value in flow.roots.items():
            base[row, flow.index[key]] += value
        for column, (a, b, f) in enumerate(flow.links):
            source[row, column] = flow.index[a]
            target[row, column] = flow.index[b]
            factor[row, column] = f
    value = base.clone()
    active = torch.ones(count, dtype=torch.bool, device=device)
    for _ in range(MAX_ITERATIONS):
        contribution = value.gather(1, source) * factor
        nxt = base.clone()
        nxt.scatter_add_(1, target, contribution)
        delta = (nxt - value).abs().max(dim=1).values
        value = torch.where(active.unsqueeze(1), nxt, value)
        active = active & (delta >= EPSILON)
        if not bool(active.any()):
            break
    result = []
    for row, flow in enumerate(flows):
        row_values = value[row]
        result.append({key: float(row_values[idx]) + flow.locals.get(key, 0.0)
                       for key, idx in flow.index.items()})
    return result


def _solve_direct(flows, device):
    """(I - A)*p = r одним матричным решением на видеокарте.

    Матрица и правые части собираются плоским scatter_add_: без питоновских
    присваиваний в тензор и без лишних синхронизаций с видеокартой.
    """
    size = max(len(flow.index) for flow in flows)
    count = len(flows)
    base_rows = []
    base_cols = []
    base_vals = []
    rows = []
    sources = []
    targets = []
    factors = []
    for row, flow in enumerate(flows):
        for key, value in flow.roots.items():
            base_rows.append(row)
            base_cols.append(flow.index[key])
            base_vals.append(value)
        for a, b, f in flow.links:
            rows.append(row)
            sources.append(flow.index[a])
            targets.append(flow.index[b])
            factors.append(-f)
    flat_base = torch.zeros(count * size, dtype=torch.float64, device=device)
    if base_rows:
        flat_base.scatter_add_(
            0,
            torch.tensor(base_rows, dtype=torch.long, device=device) * size
            + torch.tensor(base_cols, dtype=torch.long, device=device),
            torch.tensor(base_vals, dtype=torch.float64, device=device))
    base = flat_base.view(count, size)
    flat_matrix = torch.zeros(count * size * size, dtype=torch.float64, device=device)
    if rows:
        flat_matrix.scatter_add_(
            0,
            torch.tensor(rows, dtype=torch.long, device=device) * (size * size)
            + torch.tensor(targets, dtype=torch.long, device=device) * size
            + torch.tensor(sources, dtype=torch.long, device=device),
            torch.tensor(factors, dtype=torch.float64, device=device))
    matrix = flat_matrix.view(count, size, size)
    matrix += torch.eye(size, dtype=torch.float64, device=device)
    try:
        solved = torch.linalg.solve(matrix, base.unsqueeze(-1)).squeeze(-1).cpu().tolist()
    except RuntimeError:
        solved = _solve_rows(matrix, base, device)
    result = []
    for row, flow in enumerate(flows):
        row_values = solved[row]
        if row_values is None:
            result.append(flow.solve())
            continue
        result.append({key: row_values[idx] + flow.locals.get(key, 0.0)
                       for key, idx in flow.index.items()})
    return result


def _solve_rows(matrix, base, device):
    """Запасной путь для вырожденного блока: остальные строки решаем прямо, вырожденную — итерациями Java."""
    solved = []
    for row in range(matrix.shape[0]):
        try:
            values = torch.linalg.solve(matrix[row], base[row])
            solved.append(values.cpu().tolist())
        except RuntimeError:
            solved.append(None)
    return solved

def run_many(worlds, limit=4000, device="cuda", direct=True):
    """Симуляция пакета миров во времени (тики заданий, дорога, износ, станции)."""
    count = len(worlds)
    states = []
    for world in worlds:
        states.append({
            "world": world.copy(),
            "free_at": {},
            "wear": {},
            "running": [],
            "seq": [0],
            "now": 0,
            "stage_start": 0,
            "idle": 0,
            "trail": [],
            "stage_steps": {},
            "stage_ticks": {},
            "streak": {},
            "strategist": [],
            "finished": False,
            "stuck": "",
            "ticks": 0,
            "stage": world.stage,
        })
    alive = set(range(count))
    for _step in range(limit):
        if not alive:
            break
        for index in list(alive):
            state = states[index]
            world = state["world"]
            now = state["now"]
            for spirit in world.kits:
                state["free_at"].setdefault(spirit, now)
            while state["running"] and state["running"][0][0] <= now:
                _at, _seq, work = heapq.heappop(state["running"])
                finish(world, work, state["wear"])
            stage_before = world.stage
            promote(world)
            if world.stage != stage_before:
                state["stage_ticks"][stage_before] = now - state["stage_start"]
                state["stage_start"] = now
            if world.stage > LAST_STAGE:
                state["finished"] = True
                state["ticks"] = now
                state["stage"] = LAST_STAGE
                alive.discard(index)
        if not alive:
            break
        indexes = sorted(alive)
        planners = []
        for index in indexes:
            planner = Planner(states[index]["world"].graph, states[index]["world"].state())
            planner.prepare()
            planners.append(planner)
        scores = _solve_flows([planner.flow for planner in planners], device, direct)
        for planner, score in zip(planners, scores):
            planner.apply_edges(score)
        scores = _solve_flows([planner.flow for planner in planners], device, direct)
        for index, planner, score in zip(indexes, planners, scores):
            state = states[index]
            world = state["world"]
            now = state["now"]
            plan = planner.result(score)
            answer(world, plan, state["strategist"])
            log = (state["trail"], state["stage_steps"], state["streak"])
            started = assign(world, plan, now, state["free_at"], state["running"],
                             state["seq"], log)
            state["idle"] = 0 if (started or state["running"]) else state["idle"] + 1
            if state["idle"] > IDLE_LIMIT:
                state["stuck"] = "нет выполнимых заданий: " + str(plan["tasks"])
                alive.discard(index)
                continue
            next_free = min(state["free_at"].values()) if state["free_at"] else now + IDLE_TICKS
            next_done = state["running"][0][0] if state["running"] else MAX_INT
            state["now"] = max(now + 1, min(next_free, next_done))

    outcomes = []
    for state in states:
        world = state["world"]
        stuck = state["stuck"]
        if not state["finished"] and not stuck:
            stuck = "за " + str(limit) + " решений не дошли до конца этапа " + str(world.stage)
        outcomes.append({
            "steps": len(state["trail"]),
            "ticks": state["now"] if not state["finished"] else state["ticks"],
            "finished": state["finished"],
            "stuck": stuck,
            "trail": state["trail"],
            "stage_steps": state["stage_steps"],
            "stage_ticks": state["stage_ticks"],
            "stage": state["stage"] if state["finished"] else world.stage,
            "strategist": state["strategist"],
        })
    return outcomes


def run(world, limit=4000, device="cuda"):
    return run_many([world], limit, device)[0]


def load_graph(export):
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    recipes = os.path.join(root, "src", "main", "resources", "data", "theminespirit", "board",
                           "graph-recipes.json")
    return Graph.load_merged(os.path.join(export, "graph.json"), recipes)


def main(argv=None):
    parser = argparse.ArgumentParser(description="Пакетная симуляция графа поселения на torch")
    parser.add_argument("--export", default=os.path.join(
        os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
        "TestResults", "graph-export"))
    parser.add_argument("--limit", type=int, default=4000)
    parser.add_argument("--device", default="cuda")
    args = parser.parse_args(argv)
    graph = load_graph(args.export)
    worlds = baselines(graph)
    outcomes = run_many(worlds, args.limit, args.device)
    print("torch:", None if torch is None else torch.__version__,
          "cuda:", bool(torch is not None and torch.cuda.is_available()))
    for index, outcome in enumerate(outcomes):
        print(f"старт {index}: шагов {outcome['steps']}, тиков {outcome['ticks']}, "
              f"этап {outcome['stage']}, "
              f"{'готово' if outcome['finished'] else 'ЗАСТРЯЛ: ' + outcome['stuck']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
