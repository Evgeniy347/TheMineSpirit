package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.LevelingRules;
import com.theminespirit.core.board.SettlementLevel;
import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import java.util.List;
import java.util.Optional;
import java.util.function.IntBinaryOperator;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class PlotHeights {
    private static final Logger LOGGER = LogUtils.getLogger();

    record Draft(ColumnMask area, BlockCoord center, int homes, String wood, int above) {}

    private PlotHeights() {}

    static Optional<StageSite> finalSite(TownPlan town, BlockCoord center) {
        int stage = DataTables.INSTANCE.stages().current().stages().size();
        return SettlementLevel.site(town, stage, center.y()).or(() -> SettlementLevel.site(town, 1, center.y()));
    }

    static int waterAbove(ServerLevel level, ColumnMask area) {
        int above = Integer.MIN_VALUE;
        for (ColumnMask.Cell cell : area.cells()) {
            int water = GroundProbe.waterTop(level, cell.x(), cell.z(), Integer.MIN_VALUE);
            if (water != Integer.MIN_VALUE) {
                above = Math.max(above, water + 1);
            }
        }
        return above;
    }

    static SettlementPlot surveyed(
            BoardService service, SettlementPlotStore store, Draft draft, IntBinaryOperator feet, long now) {
        ColumnMask area = draft.area();
        BlockCoord center = draft.center();
        String wood = draft.wood();
        int above = draft.above();
        int homes = draft.homes();
        SettlementPlot plot = SettlementLevel.survey(center, area, feet, wood, above, rules());
        store.fix(plot);
        String note = "высота " + SettlementLevel.of(plot) + ", не ниже воды выше " + above;
        LOGGER.info(
                "{}; мин {} макс {} по {} колонкам финального этапа, центр ({}, {}) по {} домам духов, порода {}",
                note,
                plot.min(),
                plot.max(),
                plot.columns(),
                plot.anchor().x(),
                plot.anchor().z(),
                homes,
                wood);
        service.journal().recordEntry(plot.journalEntry(now, ConfigStamp.current(), List.of(note)));
        return plot;
    }

    static SettlementPlot rechecked(
            BoardService service, SettlementPlotStore store, SettlementPlot stored, IntBinaryOperator feet, long now) {
        if (service.board().ledger().has(BoardCase.CLEAR_GROUNDS, 1)) {
            store.markLeveled();
        }
        return stored;
    }

    private static LevelingRules rules() {
        return DataTables.INSTANCE.leveling().current();
    }
}
