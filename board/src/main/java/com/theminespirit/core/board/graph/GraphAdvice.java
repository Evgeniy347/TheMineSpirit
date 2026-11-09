package com.theminespirit.core.board.graph;

import java.util.List;

final class GraphAdvice {
    private GraphAdvice() {}

    static void standing(SupplyGraph graph, GraphState state, List<GraphPlan.Advice> advice) {
        for (SupplyGraph.Building building : graph.buildings().values()) {
            if (state.built(building.id()) == GraphState.Built.BUILT) {
                building.advice().forEach(rule -> advice.add(new GraphPlan.Advice(building.id(), rule)));
            }
        }
    }
}
