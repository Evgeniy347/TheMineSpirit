package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.SettlementLevel;
import com.theminespirit.core.town.SettlementPlot;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class PlotWood {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PlotWood() {}

    static SettlementPlot checked(
            ServerLevel level, BoardService service, SettlementPlotStore store, SettlementPlot plot, long now) {
        if (service.board().ledger().has(BoardCase.CLEAR_GROUNDS, 1)) {
            return plot;
        }
        Optional<String> ruled =
                SettlementWood.biomeRules(service.stock().available(), SettlementWood.biomeAt(level, plot.anchor()));
        if (ruled.isEmpty() || ruled.get().equals(plot.wood())) {
            return plot;
        }
        SettlementPlot fixed = plot.withWood(ruled.get());
        store.fix(fixed);
        LOGGER.info(
                "[plot] порода плана {} → {}: запас брёвен меньше 16 — решает биом (INC-62)",
                plot.wood(),
                fixed.wood());
        service.journal().recordEntry(SettlementLevel.journalEntry(fixed, now, ConfigStamp.current()));
        return fixed;
    }
}
