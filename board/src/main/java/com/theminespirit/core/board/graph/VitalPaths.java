package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.BoardCase;
import java.util.Map;

final class VitalPaths {
    private VitalPaths() {}

    static void note(
            SupplyGraph graph, GraphState state, String name, GraphEvent event, Map<String, GraphPlan.Notice> notices) {
        VitalNeeds.Need need = VitalNeeds.byEvent(name).orElse(null);
        if (need == null || path(graph, state, event)) {
            return;
        }
        notices.putIfAbsent(
                "vital:" + need.id(),
                new GraphPlan.Notice(
                        "vital:" + need.id(), need.blocked() + " — нужен источник, путь графа или помощь стратега"));
    }

    private static boolean path(SupplyGraph graph, GraphState state, GraphEvent event) {
        return event.tasks().stream()
                .filter(GraphEvent.Task::implemented)
                .filter(task -> task.boardCase() != BoardCase.ACTIVITY_SENSE)
                .filter(task -> GraphEventFacts.applies(graph, state, task))
                .anyMatch(task -> GraphEventFacts.feasible(graph, state, task));
    }
}
