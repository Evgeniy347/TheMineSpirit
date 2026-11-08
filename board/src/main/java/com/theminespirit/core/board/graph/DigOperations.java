package com.theminespirit.core.board.graph;

import org.jspecify.annotations.Nullable;

final class DigOperations {
    static final String RESCUE = "rescue";

    @FunctionalInterface
    interface Push {
        void push(@Nullable String from, String node, double amount);
    }

    private DigOperations() {}

    static void roots(SupplyGraph graph, GraphState state, Push push) {
        for (GraphFacts.Spirit spirit : state.facts().spirits().values()) {
            if (!spirit.trapped() && !spirit.stuck()) {
                continue;
            }
            if (spirit.stuck()) {
                push.push(null, RESCUE, base(graph, RESCUE));
            }
            if (spirit.trapped()) {
                String node = DigFacts.node(spirit.trap());
                push.push(null, node, base(graph, node));
            }
            push.push(null, DigFacts.TRAIL_CLEAN, base(graph, DigFacts.TRAIL_CLEAN));
        }
        if (!state.facts().planBreaches().isEmpty()) {
            push.push(null, DigFacts.TRAIL_CLEAN, base(graph, DigFacts.TRAIL_CLEAN));
        }
    }

    private static double base(SupplyGraph graph, String node) {
        SupplyGraph.Item item = graph.items().get(node);
        return item == null ? 40 : Math.max(1, item.base());
    }
}
