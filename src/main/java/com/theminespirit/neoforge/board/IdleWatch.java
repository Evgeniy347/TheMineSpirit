package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import org.slf4j.Logger;

final class IdleWatch {
    private static final Logger LOGGER = LogUtils.getLogger();

    private long idleSince = -1;

    void tick(BoardService service, List<SpiritEntity> spirits, long now) {
        if (spirits.isEmpty()
                || spirits.stream().anyMatch(spirit -> spirit.taskStatus().orElse(null) == RunStatus.RUNNING)) {
            idleSince = -1;
            return;
        }
        if (idleSince < 0) {
            idleSince = now;
            return;
        }
        if (now - idleSince < BoardTuning.caseFailPauseCapTicks()) {
            return;
        }
        long idleTicks = now - idleSince;
        int released = service.board().releaseDetached();
        idleSince = now;
        if (released > 0) {
            LOGGER.info(
                    "[idle] все духи простаивают {} тиков: снято подряд-снятий с кейсов {}, доска снова раздаёт",
                    idleTicks,
                    released);
        }
    }
}
