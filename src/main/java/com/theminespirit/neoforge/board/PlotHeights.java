package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.LevelingRules;
import com.theminespirit.core.board.PlotHeight;
import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.StageSite;
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
            StageSite draft,
            int homes,
            String wood,
            IntBinaryOperator feet,
            long now) {
        ColumnMask area = draft.level();
        BlockCoord center = draft.anchor();
        PlotHeight height = PlotHeight.choose(area, feet, rules());
        SettlementPlot plot = SettlementPlot.survey(center, area, feet, wood, OptionalInt.of(height.level()));
        store.fix(plot);
        LOGGER.info(
                "{}; медиана {}, мин {} макс {} по {} колонкам площадок этапа, центр ({}, {}) по {} домам духов, "
                        + "порода {}",
                height.describe(),
                SettlementPlot.survey(center, area, feet, wood, OptionalInt.empty())
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
            store.markLeveled();
        }
        Optional<StageSite> site = store.site();
        if (store.leveled() || site.isEmpty()) {
            return stored;
        }
        StageSite current = site.orElseThrow();
        BlockCoord anchor = stored.anchor();
        IntBinaryOperator plotFeet = (x, z) -> current.mineColumn(x, z) ? anchor.y() : feet.applyAsInt(x, z);
        Optional<PlotHeight> better = PlotHeight.recheck(stored, current.level(), plotFeet, rules());
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
