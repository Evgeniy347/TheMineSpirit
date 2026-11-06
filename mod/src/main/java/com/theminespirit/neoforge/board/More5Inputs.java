package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.More5Rules;
import java.util.LinkedHashSet;
import java.util.Set;

final class More5Inputs {
    private More5Inputs() {}

    static GraphState events(GraphState state, Set<String> more) {
        Set<String> events = new LinkedHashSet<>(state.events());
        events.addAll(more);
        return new GraphState(
                state.stock(),
                state.stage(),
                state.kits(),
                state.buildings(),
                state.materials(),
                state.storageFreePercent(),
                Set.copyOf(events),
                state.world(),
                state.distances(),
                state.stations(),
                state.orders(),
                state.facts());
    }

    static int darkMineCells(int raw) {
        return raw >= More5Rules.MINE_DARK_MIN ? raw : 0;
    }
}
