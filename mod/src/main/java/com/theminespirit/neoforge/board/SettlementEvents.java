package com.theminespirit.neoforge.board;

import java.util.Set;
import net.minecraft.server.level.ServerLevel;

final class SettlementEvents {
    private SettlementEvents() {}

    static void add(Set<String> events, ServerLevel level, BoardService service, FirstStepsRunner.StageScan scan) {
        if (ChestDemolition.pending(level, service)) {
            events.add(ChestDemolition.EVENT);
        }
        if (OverloadPost.pending(SpiritCensus.all(level))) {
            events.add(OverloadPost.EVENT);
        }
        if (AccessScan.blocked(level, service, scan.stage(), scan.home())) {
            events.add(AccessScan.EVENT);
        }
    }
}
