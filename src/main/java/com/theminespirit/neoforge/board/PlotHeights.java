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
import java.util.List;
import java.util.function.IntBinaryOperator;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class PlotHeights {
    private static final Logger LOGGER = LogUtils.getLogger();

    record Draft(ColumnMask area, BlockCoord center, int homes, String wood, int above) {}

    private PlotHeights() {}

    static SettlementPlot remeasured(
            BoardService service,
            SettlementPlotStore store,
            ServerLevel level,
            SettlementPlot stored,
            ColumnMask area,
            IntBinaryOperator feet,
            long now) {
        int above = SettlementGround.waterAbove(level, area);
        SettlementPlot next = SettlementLevel.remeasure(stored, area, feet, above, rules());
        store.settle(next);
        int before = SettlementLevel.of(stored);
        int after = SettlementLevel.of(next);
        LOGGER.info(
                "[plot] высота по SettlementLevel v1: {} → {} (старое правило считало по площадке этапа 1)",
                before,
                after);
        service.journal()
                .recordEntry(next.journalEntry(
                        now,
                        ConfigStamp.current(),
                        List.of("высота по SettlementLevel v1: " + before + " → " + after)));
        return next;
    }

    static SettlementPlot surveyed(
            BoardService service, SettlementPlotStore store, Draft draft, IntBinaryOperator feet, long now) {
        ColumnMask area = draft.area();
        BlockCoord center = draft.center();
        String wood = draft.wood();
        int above = draft.above();
        int homes = draft.homes();
        SettlementPlot plot = SettlementLevel.survey(center, area, feet, wood, above, rules());
        store.settle(plot);
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

    static SettlementPlot markLeveled(BoardService service, SettlementPlotStore store, SettlementPlot stored) {
        if (service.board().ledger().has(BoardCase.CLEAR_GROUNDS, 1)) {
            store.markLeveled();
        }
        return stored;
    }

    private static LevelingRules rules() {
        return DataTables.INSTANCE.leveling().current();
    }
}
