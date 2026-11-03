package com.theminespirit.neoforge.board;

import com.theminespirit.core.town.StageSite;
import net.minecraft.server.level.ServerLevel;

final class StageSurroundings {
    private StageSurroundings() {}

    static void tick(ServerLevel level, BoardService service, long now) {
        StageSite stage = SettlementPlotStore.get(level).site().orElse(null);
        if (stage == null) {
            return;
        }
        PlotAudit.tick(level, service, stage, now);
        OrchardHarvest.tick(level, service, stage, now);
    }
}
