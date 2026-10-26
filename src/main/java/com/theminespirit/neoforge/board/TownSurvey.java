package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.StageSites;
import com.theminespirit.core.town.TerrainGrid;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownPlanner;
import com.theminespirit.core.town.TownRules;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class TownSurvey {
    static final String JOURNAL_ID = "town";
    private static final Logger LOGGER = LogUtils.getLogger();

    private TownSurvey() {}

    static TownPlan town(
            ServerLevel level, BoardService service, SettlementPlotStore store, BlockCoord center, long now) {
        Optional<TownPlan> kept = store.town().filter(plan -> near(plan.anchor(), center) && StageSites.complete(plan));
        if (kept.isPresent()) {
            return kept.orElseThrow();
        }
        Optional<SettlementPlot> old = store.plot();
        TownRules rules = DataTables.INSTANCE.town().current();
        TerrainGrid grid = GroundProbe.terrain(level, center, rules.ground().probeRadius());
        TownPlan plan = TownPlanner.plan(
                grid, center, rules, DataTables.INSTANCE.stages().current().plans());
        store.fixTown(plan);
        String cause = old.isPresent()
                ? "старый участок у " + old.orElseThrow().anchor() + " заменён планом"
                : "план поселения размечен";
        LOGGER.info(
                "[plot] {}: якорь {}, площадь {}, построек {}, столбов ограды {}, калиток {}, не поместилось {}",
                cause,
                center,
                plan.area(),
                plan.placements().size(),
                plan.fence().posts().size(),
                plan.fence().gates().size(),
                plan.unplaced());
        service.journal()
                .recordEntry(BoardLog.note(
                        JOURNAL_ID,
                        BoardCase.CLEAR_GROUNDS,
                        now,
                        List.of(
                                cause,
                                "x=" + center.x(),
                                "z=" + center.z(),
                                "area=" + plan.area(),
                                "buildings=" + plan.placements().size(),
                                "unplaced=" + String.join(",", plan.unplaced())),
                        null,
                        ConfigStamp.current()));
        return plan;
    }

    private static boolean near(BlockCoord anchor, BlockCoord center) {
        return Math.abs(anchor.x() - center.x()) <= SettlementPlot.DRIFT
                && Math.abs(anchor.z() - center.z()) <= SettlementPlot.DRIFT;
    }
}
