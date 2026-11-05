package com.theminespirit.core.board.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GraphWayIndex {
    record Index(Map<String, SupplyGraph.Way> byId, Map<String, List<SupplyGraph.Way>> byMakes) {}

    private static final Map<List<SupplyGraph.Way>, Index> CACHE = Collections.synchronizedMap(new IdentityHashMap<>());

    private GraphWayIndex() {}

    static Index of(List<SupplyGraph.Way> ways) {
        return CACHE.computeIfAbsent(ways, GraphWayIndex::build);
    }

    private static Index build(List<SupplyGraph.Way> ways) {
        Map<String, SupplyGraph.Way> byId = new LinkedHashMap<>();
        Map<String, List<SupplyGraph.Way>> byMakes = new LinkedHashMap<>();
        for (SupplyGraph.Way way : ways) {
            byId.putIfAbsent(way.id(), way);
            byMakes.computeIfAbsent(way.makes(), ignored -> new ArrayList<>()).add(way);
        }
        return new Index(Map.copyOf(byId), Map.copyOf(byMakes));
    }
}
