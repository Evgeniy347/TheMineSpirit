package com.theminespirit.core.board;

import com.theminespirit.core.board.graph.GraphState;

public final class ExpeditionRule {
    private ExpeditionRule() {}

    public static boolean ready(GraphState.Built mine, boolean oreLacking) {
        return mine == GraphState.Built.BUILT && oreLacking;
    }
}
