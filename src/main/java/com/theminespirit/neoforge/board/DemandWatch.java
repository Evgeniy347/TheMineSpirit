package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.BuildingLevels;
import com.theminespirit.core.board.DemandRules;
import com.theminespirit.core.board.SettlementStage;
import com.theminespirit.core.board.WoolRules;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class DemandWatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static String last = "";

    private DemandWatch() {}

    static void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        BoardLedger ledger = service.board().ledger();
        DemandRules rules = DataTables.INSTANCE.demand().current();
        ChestSpaceWatch.Space space = ChestSpaceWatch.space(level);
        DemandRules.Facts facts = new DemandRules.Facts(
                spirits.size(),
                WoolRules.bedCount(service.stock()),
                space.slots(),
                space.freePercent(),
                highest(ledger, BoardCase.BUILD_HOUSE),
                highest(ledger, BoardCase.BUILD_WAREHOUSE),
                SettlementStage.missing(ledger),
                ledger.has(BoardCase.LIGHTING, 1) ? 0 : 1);
        Optional<DemandRules.Need> need = rules.next(facts);
        report(need, now);
    }

    private static void report(Optional<DemandRules.Need> need, long now) {
        String line = need.map(value -> value.kind() + ":" + value.subject() + ":" + value.level())
                .orElse("");
        if (line.equals(last)) {
            return;
        }
        last = line;
        if (need.isEmpty()) {
            LOGGER.info("[demand] все нужды поселения закрыты: следующая стройка выбирается этапом или графом");
            return;
        }
        DemandRules.Need value = need.orElseThrow();
        LOGGER.info(
                "[demand] следующая стройка по нужде {}: {} уровня {} (тик {})",
                value.kind(),
                value.subject(),
                value.level(),
                now);
    }

    private static int highest(BoardLedger ledger, BoardCase boardCase) {
        int highest = 0;
        int max = BuildingLevels.maxLevel(boardCase);
        for (int level = 1; level <= max; level++) {
            if (ledger.has(boardCase, level)) {
                highest = level;
            }
        }
        return highest;
    }
}
