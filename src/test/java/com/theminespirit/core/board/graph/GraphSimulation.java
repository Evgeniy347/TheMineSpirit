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

    record Outcome(
            int reachedStage,
            int steps,
            List<Step> trail,
            String stuck,
            List<String> strategist,
            Map<Integer, Integer> stageSteps) {
        boolean finished() {
            return stuck.isEmpty();
        }
    }

    @FunctionalInterface
    interface Disturbance {
        Disturbance NONE = (world, last) -> {};

        void after(GraphWorld world, GraphPlan.Task last);
    }

    private GraphSimulation() {}

    static Outcome run(GraphWorld start, int limit) {
        return run(start, limit, Disturbance.NONE);
    }

    static Outcome run(GraphWorld start, int limit, Disturbance disturbance) {
        GraphWorld world = start.copy();
        List<Step> trail = new ArrayList<>();
        Map<String, Integer> streak = new LinkedHashMap<>();
        List<String> strategist = new ArrayList<>();
        Map<Integer, Integer> stageSteps = new LinkedHashMap<>();
        for (int step = 0; step < limit; step++) {
            promote(world);
            if (world.stage > LAST_STAGE) {
                return new Outcome(LAST_STAGE, step, trail, "", strategist, stageSteps);
            }
            GraphPlan plan = world.plan();
            answer(world, plan, strategist);
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
                        strategist,
                        stageSteps);
            }
            GraphPlan.Task task = next.orElseThrow();
            trail.add(new Step(world.stage, task.id(), task.priority()));
            stageSteps.merge(world.stage, 1, Integer::sum);
            apply(world, task);
            streak.merge(task.id(), 1, Integer::sum);
            disturbance.after(world, task);
        }
        return new Outcome(
                world.stage,
                limit,
                trail,
                "за " + limit + " шагов не дошли до конца этапа " + world.stage + ": чаще всего " + top(streak)
                        + "; не построено " + unbuilt(world),
                strategist,
                stageSteps);
    }

    private static void answer(GraphWorld world, GraphPlan plan, List<String> strategist) {
        for (GraphPlan.Notice notice : plan.notices()) {
            SupplyGraph.Building wanted = world.graph.buildings().get(notice.building());
            if (wanted != null && wanted.stage() <= world.stage && world.buildings.get(wanted.id()) != Built.BUILT) {
                world.buildings.put(wanted.id(), Built.PLANNED);
                strategist.add(world.stage + ":" + wanted.id());
            }
        }
    }

    private static void promote(GraphWorld world) {
        while (world.stage <= LAST_STAGE && unbuilt(world).isEmpty()) {
            world.stage++;
            int stage = world.stage;
            world.graph.buildings().values().stream()
                    .filter(building -> building.stage() == stage && world.buildings.get(building.id()) != Built.BUILT)
                    .forEach(building -> world.buildings.put(building.id(), Built.PLANNED));
        }
    }

    static List<String> unbuilt(GraphWorld world) {
        return world.graph.buildings().values().stream()
                .filter(building -> building.stage() <= world.stage)
                .filter(building -> world.buildings.get(building.id()) != Built.BUILT)
                .map(SupplyGraph.Building::id)
                .toList();
    }

    private static void apply(GraphWorld world, GraphPlan.Task task) {
        task.consumes().forEach((item, count) -> consume(world, item, count));
        if (task.id().startsWith("event:")) {
            world.events.remove(task.id().split(":", 3)[1]);
            return;
        }
        if (task.id().startsWith("building:")) {
            world.buildings.put(task.makes(), Built.BUILT);
            if (task.makes().equals(world.graph.storage().building())) {
                world.storageFree = Math.max(world.storageFree, 60);
            }
            return;
        }
        if (world.graph.isTool(task.makes())) {
            int split = task.makes().indexOf('_');
            String kind = task.makes().substring(split + 1);
            int tier = world.graph.rank(task.makes().substring(0, split));
            world.kits.get(task.spirit().orElseThrow()).merge(kind, tier, Math::max);
            return;
        }
        int yield = world.graph.way(task.id()).orElseThrow().yield();
        world.stock.merge(task.makes(), yield, Integer::sum);
    }

    private static void consume(GraphWorld world, String item, int count) {
        if (!world.graph.isGroup(item)) {
            world.stock.merge(item, -count, Integer::sum);
            return;
        }
        int left = count;
        for (String member : world.graph.members(item)) {
            int take = Math.min(left, Math.max(0, world.stock.getOrDefault(member, 0)));
            world.stock.merge(member, -take, Integer::sum);
            left -= take;
        }
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
