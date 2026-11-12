package com.theminespirit.core.board.graph;

public final class GraphPriorities {
    public static final int LIFE_SPAN = 30;

    private GraphPriorities() {}

    public static int life(SupplyGraph graph) {
        return Math.max(2, graph.maxPriority() - LIFE_SPAN);
    }

    public static int need(SupplyGraph graph) {
        return life(graph) - 1;
    }

    static double edge(SupplyGraph graph, double score, double steps) {
        return GraphFlow.shift(score, steps, work(graph));
    }

    public static int work(SupplyGraph graph) {
        return Math.max(1, need(graph) - VitalNeeds.BAND - 1);
    }
}
