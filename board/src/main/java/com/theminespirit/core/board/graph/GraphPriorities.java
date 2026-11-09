package com.theminespirit.core.board.graph;

public final class GraphPriorities {
    private GraphPriorities() {}

    public static int work(SupplyGraph graph) {
        int max = graph.maxPriority();
        return max > 1 ? max - 2 : max;
    }

    public static int need(SupplyGraph graph) {
        int max = graph.maxPriority();
        return max > 0 ? max - 1 : max;
    }
}
