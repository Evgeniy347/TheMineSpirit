package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.SettlementPlot;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

final class StagePlotSurvey {
    private StagePlotSurvey() {}

    static Optional<SettlementPlot> plotOf(
            ServerLevel level, BoardService service, List<? extends HomedSpirit> spirits, long now) {
        List<BlockCoord> homes = SettlementHome.homes(spirits);
        SettlementPlotStore store = SettlementPlotStore.get(level);
        BlockCoord center = SettlementPlot.center(homes);
        TownSurvey.town(level, service, store, center, now);
        Optional<SettlementPlot> stored = store.plot();
        if (stored.isPresent() && stored.orElseThrow().covers(center)) {
            return Optional.of(PlotWood.checked(level, service, store, stored.orElseThrow(), now));
        }
        return Optional.empty();
    }
}
