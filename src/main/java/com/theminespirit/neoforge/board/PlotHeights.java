package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.LevelingRules;
import com.theminespirit.core.board.PlotHeight;
import com.theminespirit.core.board.SettlementLayout;
import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntBinaryOperator;
import org.slf4j.Logger;

final class PlotHeights {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PlotHeights() {}

    static SettlementPlot surveyed(
            BoardService service,
            SettlementPlotStore store,
            BlockCoord center,
            int homes,
            String wood,
            IntBinaryOperator feet,
            long now) {
        PlotHeight height = PlotHeight.choose(center, SettlementLayout.LEVEL_HALF, feet, rules());
        SettlementPlot plot =
                SettlementPlot.survey(center, SettlementLayout.LEVEL_HALF, feet, wood, OptionalInt.of(height.level()));
        store.fix(plot);
        LOGGER.info(
                "{}; медиана {}, мин {} макс {} по {} колонкам, центр ({}, {}) по {} домам духов, порода {}",
                height.describe(),
                SettlementPlot.survey(center, SettlementLayout.LEVEL_HALF, feet, wood)
                        .anchor()
                        .y(),
                plot.min(),
                plot.max(),
                plot.columns(),
                plot.anchor().x(),
                plot.anchor().z(),
                homes,
                wood);
        service.journal().recordEntry(plot.journalEntry(now, ConfigStamp.current(), List.of(height.describe())));
        return plot;
    }

    static SettlementPlot rechecked(
            BoardService service, SettlementPlotStore store, SettlementPlot stored, IntBinaryOperator feet, long now) {
        if (service.board().ledger().has(BoardCase.CLEAR_GROUNDS, 1)) {
            return stored;
        }
        BlockCoord anchor = stored.anchor();
        IntBinaryOperator plotFeet =
                (x, z) -> SettlementLayout.mineColumn(anchor, x, z) ? anchor.y() : feet.applyAsInt(x, z);
        Optional<PlotHeight> better = PlotHeight.recheck(stored, SettlementLayout.LEVEL_HALF, plotFeet, rules());
        if (better.isEmpty() || better.get().level() == anchor.y()) {
            return stored;
        }
        SettlementPlot fixed = stored.withLevel(better.get().level());
        store.fix(fixed);
        LOGGER.info(
                "{}; сохранённая высота {} не даёт срезки на подсыпку с запасом — план пересчитан",
                better.get().describe(),
                anchor.y());
        service.journal()
                .recordEntry(fixed.journalEntry(
                        now, ConfigStamp.current(), List.of(better.get().describe())));
        return fixed;
    }

    private static LevelingRules rules() {
        return DataTables.INSTANCE.leveling().current();
    }
}
