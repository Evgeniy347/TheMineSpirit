package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import org.slf4j.Logger;

final class StageClose {
    private static final Logger LOGGER = LogUtils.getLogger();

    private StageClose() {}

    static void built(BoardService service, BoardCase caseValue, int parts) {
        if (!service.board().ledger().has(caseValue, 1)) {
            if (service.board().statusOf(FirstStepsRunner.partId(caseValue, 1)).isPresent()) {
                service.board().built(FirstStepsRunner.partId(caseValue, 1));
            } else {
                service.board().ledger().complete(caseValue, 1);
            }
            LOGGER.info("[layout] {} уже стоит по миру: отметка поставлена без задания", caseValue);
        }
        for (int part = 2; part <= parts; part++) {
            service.board().built(FirstStepsRunner.partId(caseValue, part));
        }
    }

    static void reopen(BoardService service, BoardCase caseValue, int parts) {
        for (int part = 1; part <= parts; part++) {
            String id = FirstStepsRunner.partId(caseValue, part);
            if (service.board().statusOf(id).orElse(null) == BoardStatus.DONE) {
                service.reopen(id);
            }
        }
    }
}
