package com.theminespirit.core.board.graph;

final class BuildingShare {
    private static final String BUILDING = "building:";

    private BuildingShare() {}

    static double of(SupplyGraph graph, GraphState state, String key) {
        if (!key.startsWith(BUILDING)) {
            return 1.0;
        }
        String id = key.substring(BUILDING.length());
        SupplyGraph.Building building = graph.buildings().get(id);
        boolean tooled = building == null || building.tool() == null || state.bestTier(building.tool()) >= 0;
        boolean grounded = building == null
                || building.requires().stream().allMatch(required -> state.built(required) == GraphState.Built.BUILT);
        if (tooled && grounded && GraphLack.stocked(graph, state, GraphLack.materials(graph, state, id))) {
            return 1.0;
        }
        return graph.decay() * graph.decay() * graph.decay();
    }

    static double luck(SupplyGraph graph, GraphState state, boolean aboveKit, SupplyGraph.Way way) {
        if (!aboveKit || GraphLack.stocked(graph, state, way.consumes())) {
            return 1.0;
        }
        return graph.decay() * graph.decay() * graph.decay();
    }
}
