package com.theminespirit.core.board.graph;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ChestTools {
    private static final String NAMESPACE = "minecraft:";

    private ChestTools() {}

    static Set<String> covered(SupplyGraph graph, GraphState state) {
        Set<String> covered = new HashSet<>();
        for (String kind : graph.kit().kinds()) {
            int target = graph.rank(graph.kit().tier(kind, state.stage()));
            if (target < 0) {
                continue;
            }
            int have = count(graph, state, kind);
            if (have <= 0) {
                continue;
            }
            List<String> lacking = state.kits().keySet().stream()
                    .filter(spirit -> state.tier(spirit, kind) < target)
                    .sorted()
                    .toList();
            for (int index = 0; index < Math.min(have, lacking.size()); index++) {
                covered.add(kind + "@" + lacking.get(index));
            }
        }
        return Set.copyOf(covered);
    }

    private static int count(SupplyGraph graph, GraphState state, String kind) {
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
                if (split > 0 && name.substring(split + 1).equals(kind) && graph.rank(name.substring(0, split)) >= 0) {
                    count += item.getValue();
                }
            }
        }
        return count;
    }
}
