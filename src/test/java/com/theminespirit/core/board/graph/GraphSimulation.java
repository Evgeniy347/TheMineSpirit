package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

final class GraphSimulation {
    static final int LAST_STAGE = 3;
    static final int IDLE_TICKS = 100;
    static final int IDLE_LIMIT = 400;

    record Step(int stage, String task, int priority) {}

    record Outcome(
            int reachedStage,
            int steps,
            List<Step> trail,
            String stuck,
            List<String> strategist,
            Map<Integer, Integer> stageSteps,
            long ticks,
            Map<Integer, Long> stageTicks) {
        boolean finished() {
            return stuck.isEmpty();
        }
    }

    @FunctionalInterface
    interface Disturbance {
        Disturbance NONE = (world, last) -> {};

        void after(GraphWorld world, GraphPlan.Task last);
    }

    private record Work(long finishAt, String spirit, GraphPlan.Task task) {}

    private GraphSimulation() {}

    static Outcome run(GraphWorld start, int limit) {
        return run(start, limit, Disturbance.NONE);
    }

    static Outcome runUntil(GraphWorld start, int limit, int stopStage) {
        return run(start, limit, Disturbance.NONE, stopStage);
    }

    static Outcome run(GraphWorld start, int limit, Disturbance disturbance) {
        return run(start, limit, disturbance, LAST_STAGE + 1);
    }

    private static Outcome run(GraphWorld start, int limit, Disturbance disturbance, int stopStage) {
        GraphWorld world = start.copy();
        List<Step> trail = new ArrayList<>();
        Map<String, Integer> streak = new LinkedHashMap<>();
        List<String> strategist = new ArrayList<>();
        Map<Integer, Integer> stageSteps = new LinkedHashMap<>();
        Map<Integer, Long> stageTicks = new LinkedHashMap<>();
        Map<String, Long> freeAt = new HashMap<>();
        Map<String, Integer> wear = new HashMap<>();
        Queue<Work> running = new PriorityQueue<>(Comparator.comparingLong(Work::finishAt));
        long now = 0;
        long stageStart = 0;
        int idle = 0;
        for (int step = 0; step < limit; step++) {
            for (String spirit : world.kits.keySet()) {
                freeAt.putIfAbsent(spirit, now);
            }
            while (!running.isEmpty() && running.peek().finishAt() <= now) {
                Work done = running.poll();
                finish(world, done, wear);
                disturbance.after(world, done.task());
            }
            int stageBefore = world.stage;
            promote(world);
            if (world.stage != stageBefore) {
                stageTicks.put(stageBefore, now - stageStart);
                stageStart = now;
            }
            if (world.stage > LAST_STAGE || world.stage >= stopStage) {
                return new Outcome(
                        Math.min(world.stage, LAST_STAGE),
                        trail.size(),
                        trail,
                        "",
                        strategist,
                        stageSteps,
                        now,
                        stageTicks);
            }
            GraphPlan plan = world.plan();
            answer(world, plan, strategist);
            boolean started = assign(world, plan, now, freeAt, running, new Log(trail, stageSteps, streak));
            idle = started || !running.isEmpty() ? 0 : idle + 1;
            if (idle > IDLE_LIMIT) {
                return stuck(
                        world,
                        trail,
                        "нет выполнимых заданий: " + plan.tasks() + "; просьбы " + plan.notices(),
                        strategist,
                        stageSteps,
                        now,
                        stageTicks);
            }
            long nextFree =
                    freeAt.values().stream().mapToLong(Long::longValue).min().orElse(now + IDLE_TICKS);
            long nextDone = running.isEmpty() ? Long.MAX_VALUE : running.peek().finishAt();
            now = Math.max(now + 1, Math.min(nextFree, nextDone));
        }
        return stuck(
                world,
                trail,
                "за " + limit + " решений не дошли до конца этапа " + world.stage + ": чаще всего " + top(streak)
                        + "; не построено " + unbuilt(world),
                strategist,
                stageSteps,
                now,
                stageTicks);
    }

    private record Log(List<Step> trail, Map<Integer, Integer> stageSteps, Map<String, Integer> streak) {}

    private static boolean assign(
            GraphWorld world, GraphPlan plan, long now, Map<String, Long> freeAt, Queue<Work> running, Log log) {
        boolean started = false;
        Set<String> taken = new HashSet<>();
        running.forEach(work -> taken.add(work.task().id()));
        for (String spirit : List.copyOf(world.kits.keySet())) {
            if (freeAt.getOrDefault(spirit, now) > now) {
                continue;
            }
            Optional<GraphPlan.Task> next = GraphWorld.issueOrder(plan).stream()
                    .filter(task -> !taken.contains(task.id()))
                    .filter(task -> task.spirit().map(spirit::equals).orElse(true))
                    .filter(task -> stocked(world, task))
                    .findFirst();
            if (next.isEmpty()) {
                freeAt.put(spirit, now + IDLE_TICKS);
                continue;
            }
            GraphPlan.Task task = next.orElseThrow();
            taken.add(task.id());
            task.consumes().forEach((item, count) -> consume(world, item, count));
            long finishAt = now + Math.max(1, task.ticks());
            running.add(new Work(finishAt, spirit, task));
            freeAt.put(spirit, finishAt);
            log.trail().add(new Step(world.stage, task.id(), task.priority()));
            log.stageSteps().merge(world.stage, 1, Integer::sum);
            log.streak().merge(task.id(), 1, Integer::sum);
            started = true;
        }
        return started;
    }

    private static Outcome stuck(
            GraphWorld world,
            List<Step> trail,
            String why,
            List<String> strategist,
            Map<Integer, Integer> stageSteps,
            long now,
            Map<Integer, Long> stageTicks) {
        return new Outcome(world.stage, trail.size(), trail, why, strategist, stageSteps, now, stageTicks);
    }

    private static boolean stocked(GraphWorld world, GraphPlan.Task task) {
        return task.consumes().entrySet().stream().allMatch(entry -> have(world, entry.getKey()) >= entry.getValue());
    }

    private static int have(GraphWorld world, String item) {
        if (world.graph.isGroup(item)) {
            return world.graph.members(item).stream()
                    .mapToInt(member -> Math.max(0, world.stock.getOrDefault(member, 0)))
                    .sum();
        }
        return world.stock.getOrDefault(item, 0);
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
                    .filter(building -> building.stage() == stage && !building.optional())
                    .filter(building -> world.buildings.get(building.id()) != Built.BUILT)
                    .forEach(building -> world.buildings.put(building.id(), Built.PLANNED));
        }
    }

    static List<String> unbuilt(GraphWorld world) {
        return world.graph.buildings().values().stream()
                .filter(building -> building.stage() <= world.stage && !building.optional())
                .filter(building -> world.buildings.get(building.id()) != Built.BUILT)
                .map(SupplyGraph.Building::id)
                .toList();
    }

    private static void finish(GraphWorld world, Work work, Map<String, Integer> wear) {
        GraphPlan.Task task = work.task();
        if (task.id().startsWith("explore:")) {
            world.world.add(task.makes());
            return;
        }
        if (task.id().startsWith("station:")) {
            world.stations.merge(task.makes(), 2, (before, ignored) -> before + 1);
            return;
        }
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
        int at = task.id().indexOf('@');
        SupplyGraph.Way way =
                world.graph.way(at < 0 ? task.id() : task.id().substring(0, at)).orElseThrow();
        if (world.graph.isTool(task.makes())) {
            int split = task.makes().indexOf('_');
            String kind = task.makes().substring(split + 1);
            String tier = task.makes().substring(0, split);
            String owner = task.spirit().orElse(work.spirit());
            world.kits.get(owner).merge(kind, world.graph.rank(tier), Math::max);
            wear.put(owner + ":" + kind, world.graph.costs().durability(tier));
            return;
        }
        world.stock.merge(task.makes(), way.yield(), Integer::sum);
        if (way.tool() != null) {
            String key = work.spirit() + ":" + way.tool();
            int rank = world.kits.getOrDefault(work.spirit(), Map.of()).getOrDefault(way.tool(), -1);
            int fresh = rank < 0
                    ? Integer.MAX_VALUE
                    : world.graph.costs().durability(world.graph.tiers().get(rank));
            int left = wear.getOrDefault(key, fresh) - world.graph.costs().uses(way.id(), way.yield());
            if (left <= 0 && world.kits.containsKey(work.spirit())) {
                world.kits.get(work.spirit()).remove(way.tool());
                wear.remove(key);
            } else {
                wear.put(key, left);
            }
        }
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
