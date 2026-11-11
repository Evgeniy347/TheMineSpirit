package com.theminespirit.core.board.graph;

final class BuildingShare {
    private static final String BUILDING = "building:";

    private BuildingShare() {}

    static double of(SupplyGraph graph, GraphState state, String key) {
        if (!key.startsWith(BUILDING)) {
            return 1.0;
        }
        String id = key.substring(BUILDING.length());
        if (GraphLack.stocked(graph, state, GraphLack.materials(graph, state, id))) {
            return 1.0;
        }
        return graph.decay() * graph.decay() * graph.decay();
    }
}
