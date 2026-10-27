package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GraphSimulation {
    static final int LAST_STAGE = 3;

    record Step(int stage, String task, int priority) {}

    record Outcome(int reachedStage, int steps, List<Step> trail, String stuck, List<String> strategist) {
        boolean finished() {
            return stuck.isEmpty();
        }
    }

    private GraphSimulation() {}

    static Outcome run(GraphWorld start, int limit) {
        GraphWorld world = start.copy();
        List<Step> trail = new ArrayList<>();
        Map<String, Integer> streak = new LinkedHashMap<>();
        List<String> strategist = new ArrayList<>();
        for (int step = 0; step < limit; step++) {
            promote(world);
            if (world.stage > LAST_STAGE) {
                return new Outcome(LAST_STAGE, step, trail, "", strategist);
            }
            GraphPlan plan = world.plan();
            for (GraphPlan.Notice notice : plan.notices()) {
                SupplyGraph.Building wanted = GraphWorld.GRAPH.buildings().get(notice.building());
                if (wanted != null
                        && wanted.stage() <= world.stage
                        && world.buildings.get(wanted.id()) != Built.BUILT) {
                    world.buildings.put(wanted.id(), Built.PLANNED);
                    strategist.add(world.stage + ":" + wanted.id());
                }
            }
            Optional<GraphPlan.Task> next = GraphWorld.issueOrder(plan).stream().findFirst();
            if (next.isEmpty()) {
                plan = world.plan();
                next = GraphWorld.issueOrder(plan).stream().findFirst();
            }
            if (next.isEmpty()) {
                return new Outcome(
                        world.stage,
                        step,
                        trail,
                        "нет выполнимых заданий: " + plan.tasks() + "; просьбы " + plan.notices(),
                        strategist);
            }
            GraphPlan.Task task = next.orElseThrow();
            trail.add(new Step(world.stage, task.id(), task.priority()));
            apply(world, task);
            streak.merge(task.id(), 1, Integer::sum);
        }
        return new Outcome(
                world.stage,
                limit,
                trail,
                "за " + limit + " шагов не дошли до конца этапа " + world.stage + ": чаще всего " + top(streak)
                        + "; не построено " + unbuilt(world),
                strategist);
    }

    private static void promote(GraphWorld world) {
        while (world.stage <= LAST_STAGE && unbuilt(world).isEmpty()) {
            world.stage++;
            int stage = world.stage;
            GraphWorld.GRAPH.buildings().values().stream()
                    .filter(building -> building.stage() == stage)
                    .forEach(building -> world.buildings.putIfAbsent(building.id(), Built.PLANNED));
            GraphWorld.GRAPH.buildings().values().stream()
                    .filter(building -> building.stage() == stage && world.buildings.get(building.id()) == Built.ABSENT)
                    .forEach(building -> world.buildings.put(building.id(), Built.PLANNED));
        }
    }

    static List<String> unbuilt(GraphWorld world) {
        return GraphWorld.GRAPH.buildings().values().stream()
                .filter(building -> building.stage() <= world.stage)
                .filter(building -> world.buildings.get(building.id()) != Built.BUILT)
                .map(SupplyGraph.Building::id)
                .toList();
    }

    private static void apply(GraphWorld world, GraphPlan.Task task) {
        task.consumes().forEach((item, count) -> world.stock.merge(item, -count, Integer::sum));
        if (task.id().startsWith("building:")) {
            world.buildings.put(task.makes(), Built.BUILT);
            if (task.makes().equals(GraphWorld.GRAPH.storage().building())) {
                world.storageFree = Math.max(world.storageFree, 60);
            }
            return;
        }
        if (GraphWorld.GRAPH.isTool(task.makes())) {
            int split = task.makes().indexOf('_');
            String kind = task.makes().substring(split + 1);
            int tier = GraphWorld.GRAPH.rank(task.makes().substring(0, split));
            String spirit = task.spirit().orElseThrow();
            world.kits.get(spirit).merge(kind, tier, Math::max);
            return;
        }
        String way = task.id();
        int yield = GraphWorld.GRAPH.way(way).orElseThrow().yield();
        world.stock.merge(task.makes(), yield, Integer::sum);
    }

    private static String top(Map<String, Integer> streak) {
        return streak.entrySet().stream()
                .sorted((first, second) -> Integer.compare(second.getValue(), first.getValue()))
                .limit(3)
                .map(entry -> entry.getKey() + "×" + entry.getValue())
                .toList()
                .toString();
    }
}
