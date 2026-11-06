package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.graph.GraphState.Built;

public final class GraphAudit {
    private GraphAudit() {}

    public static boolean settles(GraphState state, String building) {
        if (state.built(building) == Built.BUILT) {
            return false;
        }
        GraphFacts.Building fact = state.facts().buildings().get(building);
        if (fact == null) {
            return false;
        }
        return fact.unknown() || fact.auditPercent() > 0 || !fact.lotReady();
    }
}
