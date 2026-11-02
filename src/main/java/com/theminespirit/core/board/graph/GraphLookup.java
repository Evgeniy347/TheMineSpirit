package com.theminespirit.core.board.graph;

import java.util.Optional;

final class GraphLookup {
    private static final char AT = '@';

    private GraphLookup() {}

    static Optional<String> station(SupplyGraph graph, String key) {
        int at = key.indexOf(AT);
        return graph.way(at < 0 ? key : key.substring(0, at))
                .map(SupplyGraph.Way::building)
                .filter(building -> graph.costs().stationCapacity().containsKey(building));
    }

    static int travel(SupplyGraph graph, GraphState state, String key) {
        int at = key.indexOf(AT);
        return graph.way(at < 0 ? key : key.substring(0, at))
                .map(SupplyGraph.Way::world)
                .map(resource -> graph.costs().travel(state.distances().getOrDefault(resource, 0)))
                .orElse(0);
    }
}
