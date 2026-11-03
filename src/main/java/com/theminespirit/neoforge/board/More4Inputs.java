package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.More4Rules;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

final class More4Inputs {
    private More4Inputs() {}

    static GraphState apply(
            GraphState state,
            ServerLevel level,
            List<SpiritEntity> spirits,
            FirstStepsRunner.StageScan scan,
            SupplyGraph graph) {
        return events(state, More4Rules.events(More4World.signals(state, level, spirits, scan, graph)));
    }

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
}
