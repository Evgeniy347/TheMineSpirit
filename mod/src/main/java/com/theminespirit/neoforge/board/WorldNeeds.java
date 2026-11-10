package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.SupplyGraph;
import java.util.LinkedHashMap;
import java.util.Map;

final class WorldNeeds {
    private WorldNeeds() {}

    static Map<String, Integer> of(SupplyGraph graph, Map<String, Integer> consumes, String wood) {
        Map<String, Integer> need = new LinkedHashMap<>();
        consumes.forEach((item, count) -> {
            String named = GraphNames.item(graph, item, wood);
            need.merge(
                    named.startsWith("minecraft:") ? named.substring("minecraft:".length()) : named,
                    count,
                    Integer::sum);
        });
        return Map.copyOf(need);
    }
}
