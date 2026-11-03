package com.theminespirit.core.board.graph;

import org.jspecify.annotations.Nullable;

final class DigOperations {
    @FunctionalInterface
    interface Push {
        void push(@Nullable String from, String node, double amount);
    }

    private DigOperations() {}

    static void roots(SupplyGraph graph, GraphState state, Push push) {
        for (GraphFacts.Spirit spirit : state.facts().spirits().values()) {
            if (spirit.trapped()) {
                String node = DigFacts.node(spirit.trap());
                push.push(null, node, base(graph, node));
                push.push(null, DigFacts.TRAIL_CLEAN, base(graph, DigFacts.TRAIL_CLEAN));
            }
        }
    }

    private static double base(SupplyGraph graph, String node) {
        SupplyGraph.Item item = graph.items().get(node);
        return item == null ? 40 : Math.max(1, item.base());
    }
}
