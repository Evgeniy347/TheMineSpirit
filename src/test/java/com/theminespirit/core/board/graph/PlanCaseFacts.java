package com.theminespirit.core.board.graph;

import java.util.List;
import java.util.Map;

final class PlanCaseFacts {
    private PlanCaseFacts() {}

    static GraphFacts facts(
            Map<String, GraphFacts.Building> buildings,
            List<GraphSiteFacts.Stray> strays,
            GraphSiteFacts.Terrain terrain) {
        return new GraphFacts(
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                buildings,
                strays,
                terrain,
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()));
    }

    static GraphFacts.Building building(
            String id, int percent, int damage, Map<String, Integer> missing, boolean unknown) {
        return new GraphFacts.Building(id, 1, percent, damage, missing, Map.of(), 0, null, unknown);
    }

    static GraphPlan plan(GraphWorld world, GraphFacts facts) {
        return GraphPlanner.plan(world.graph, world.state().withFacts(facts));
    }

    static boolean posted(GraphPlan plan, String id) {
        return plan.task(id)
                .filter(task -> task.feasible() && task.implemented() && task.priority() > 0)
                .isPresent();
    }

    static GraphPlan.Task task(GraphPlan plan, String id) {
        return plan.task(id)
                .orElseThrow(() -> new AssertionError("На доске нет задания " + id + ": "
                        + plan.tasks().stream().map(GraphPlan.Task::id).toList()));
    }
}
