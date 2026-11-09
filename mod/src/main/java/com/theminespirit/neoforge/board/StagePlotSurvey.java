package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.SettlementLevel;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Optional;
import java.util.function.IntBinaryOperator;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class StagePlotSurvey {
    private static final long PLAN_WARN_EVERY = 600;
    private static final Logger LOGGER = LogUtils.getLogger();

    private StagePlotSurvey() {}

    static Optional<SettlementPlot> plotOf(
            ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        List<BlockCoord> homes = SettlementHome.homes(spirits);
        SettlementPlotStore store = SettlementPlotStore.get(level);
        BlockCoord center = SettlementPlot.center(homes);
        TownPlan town = TownSurvey.town(level, service, store, center, now);
        Optional<ColumnMask> draft = SettlementLevel.finalArea(town);
        if (draft.isEmpty()) {
            if (now % PLAN_WARN_EVERY != 0) {
                return Optional.empty();
            }
            LOGGER.warn("[plot] нет финального этапа у {}: не поместилось {}", center, town.unplaced());
            return Optional.empty();
        }
        ColumnMask area = draft.orElseThrow();
        IntBinaryOperator feet = SettlementGround.feet(level, center.y());
        Optional<SettlementPlot> stored = store.plot();
        if (stored.isPresent() && stored.orElseThrow().covers(center)) {
            SettlementPlot kept = stored.orElseThrow();
            if (!store.levelMarked()) {
                kept = PlotHeights.remeasured(service, store, level, kept, area, feet, now);
            }
            kept = PlotHeights.markLeveled(service, store, kept);
            return Optional.of(PlotWood.checked(level, service, store, kept, now));
        }
        String wood = SettlementWood.survey(level, center, service.stock().available());
        int above = SettlementGround.waterAbove(level, area);
        return Optional.of(PlotHeights.surveyed(
                service, store, new PlotHeights.Draft(area, center, homes.size(), wood, above), feet, now));
    }
}
