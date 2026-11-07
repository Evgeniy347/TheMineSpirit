package com.theminespirit.core.board.graph;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

final class GraphBuildOrder {
    private static final String BUILDING = "building:";

    private GraphBuildOrder() {}

    static List<GraphPlan.Task> buildingsFirst(
            SupplyGraph graph, List<GraphPlan.Task> tasks, Predicate<String> declaredReady) {
        Set<String> supply = new LinkedHashSet<>();
        for (GraphPlan.Task task : tasks) {
            if (!task.id().startsWith(BUILDING) || !declaredReady.test(task.makes())) {
                continue;
            }
            task.consumes().keySet().forEach(item -> {
                if (graph.isGroup(item)) {
                    supply.addAll(graph.members(item));
                } else {
                    supply.add(item);
                }
            });
        }
        int maxOther = tasks.stream()
                .filter(task -> !task.id().startsWith(BUILDING))
                .filter(task -> !supply.contains(task.makes()))
                .mapToInt(GraphPlan.Task::priority)
                .max()
                .orElse(0);
        List<Integer> order = tasks.stream()
                .filter(task -> task.id().startsWith(BUILDING))
                .filter(task -> task.priority() > 0)
                .map(GraphPlan.Task::priority)
                .distinct()
                .sorted()
                .toList();
        if (order.isEmpty() || maxOther >= GraphPriorities.work(graph)) {
            return tasks;
        }
        int start = Math.max(maxOther + 1, order.get(0));
        int room = Math.max(order.get(0), GraphPriorities.work(graph) - (order.size() - 1));
        start = Math.min(start, room);
        int step = order.size() > 1 ? Math.max(1, (GraphPriorities.work(graph) - start) / (order.size() - 1)) : 1;
        Map<Integer, Integer> band = new LinkedHashMap<>();
        for (int index = 0; index < order.size(); index++) {
            band.put(order.get(index), Math.min(GraphPriorities.work(graph), start + index * step));
        }
        int maxSupply = tasks.stream()
                .filter(task -> !task.id().startsWith(BUILDING))
                .filter(task -> supply.contains(task.makes()))
                .mapToInt(GraphPlan.Task::priority)
                .max()
                .orElse(0);
        int shift = Math.max(0, start - 1 - maxSupply);
        int bandStart = start;
        return tasks.stream()
                .map(task -> {
                    boolean building = task.id().startsWith(BUILDING);
                    int priority;
                    if (building) {
                        priority = task.priority() > 0 ? band.getOrDefault(task.priority(), task.priority()) : 0;
                    } else if (supply.contains(task.makes()) && !task.consumes().isEmpty()) {
                        priority = bandStart;
                    } else if (supply.contains(task.makes())) {
                        priority = Math.min(GraphPriorities.work(graph), task.priority() + shift);
                    } else {
                        priority = task.priority();
                    }
                    if (priority == task.priority()) {
                        return task;
                    }
                    return new GraphPlan.Task(
                            task.id(),
                            task.boardCase(),
                            task.makes(),
                            task.spirit(),
                            Math.min(GraphPriorities.work(graph), priority),
                            task.consumes(),
                            task.feasible(),
                            task.chance(),
                            task.implemented(),
                            task.ticks());
                })
                .toList();
    }
}
