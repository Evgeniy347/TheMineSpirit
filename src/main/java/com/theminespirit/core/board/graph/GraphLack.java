package com.theminespirit.core.board.graph;

import java.util.List;

public final class GraphLack {
    private GraphLack() {}

    public static int threshold(SupplyGraph.Item item, GraphState state) {
        int stage = item.perSpirit() ? 1 : state.stage();
        return (int) Math.ceil(item.xmin() * state.spirits() * stage * item.k());
    }

    public static boolean lacking(SupplyGraph graph, GraphState state, String node) {
        if (graph.isGroup(node)) {
            List<String> members = graph.members(node);
            SupplyGraph.Item first = graph.items().get(members.get(0));
            int have = members.stream().mapToInt(state::count).sum();
            return first != null && have < threshold(first, state);
        }
        SupplyGraph.Item item = graph.items().get(node);
        if (item != null && item.stage() <= state.stage()) {
            return state.count(node) < threshold(item, state);
        }
        if (graph.isTool(node)) {
            int split = node.indexOf('_');
            return state.bestTier(node.substring(split + 1)) < graph.rank(node.substring(0, split));
        }
        return false;
    }
}
