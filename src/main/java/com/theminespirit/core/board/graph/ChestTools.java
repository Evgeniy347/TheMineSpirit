package com.theminespirit.core.board.graph;

import java.util.Map;

final class ChestTools {
    private static final String NAMESPACE = "minecraft:";

    private ChestTools() {}

    static boolean covers(SupplyGraph graph, GraphState state, String kind, int target) {
        if (target < 0) {
            return false;
        }
        int lacking = 0;
        for (String spirit : state.kits().keySet()) {
            if (state.tier(spirit, kind) < target) {
                lacking++;
            }
        }
        return lacking > 0 && count(graph, state, kind, target) >= lacking;
    }

    private static int count(SupplyGraph graph, GraphState state, String kind, int target) {
        int count = 0;
        for (GraphFacts.Chest chest : state.facts().chests()) {
            for (Map.Entry<String, Integer> item : chest.contents().entrySet()) {
                if (item.getValue() <= 0) {
                    continue;
                }
                String id = item.getKey();
                String name = id.startsWith(NAMESPACE) ? id.substring(NAMESPACE.length()) : id;
                if (!graph.isTool(name)) {
                    continue;
                }
                int split = name.indexOf('_');
                if (name.substring(split + 1).equals(kind) && graph.rank(name.substring(0, split)) >= target) {
                    count += item.getValue();
                }
            }
        }
        return count;
    }
}
