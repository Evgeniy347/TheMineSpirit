package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphCadence;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.entity.Entity;

final class GraphSignals {
    private GraphSignals() {}

    static Set<String> of(Set<String> events, List<? extends Entity> spirits, BoardService service, long now) {
        Set<String> alive = new HashSet<>();
        Set<String> stuck = new HashSet<>();
        for (Entity spirit : spirits) {
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
