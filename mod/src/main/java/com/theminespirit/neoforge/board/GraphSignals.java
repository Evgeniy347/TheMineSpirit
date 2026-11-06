package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphCadence;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class GraphSignals {
    private GraphSignals() {}

    static Set<String> of(Set<String> events, List<SpiritEntity> spirits, BoardService service, long now) {
        Set<String> alive = new HashSet<>();
        Set<String> stuck = new HashSet<>();
        for (SpiritEntity spirit : spirits) {
            if (spirit.isRemoved()) {
                continue;
            }
            String id = spirit.getUUID().toString();
            alive.add(id);
            if (service.board().stuck().isStuck(spirit.getUUID(), now)) {
                stuck.add(id);
            }
        }
        return GraphCadence.signals(events, alive, stuck);
    }
}
