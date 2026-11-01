package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.RepeatLog;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;

final class IdleWatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CHANNEL = "idle";

    private final RepeatLog repeats;

    IdleWatch() {
        this(BoardTuning.logSummaryTicks());
    }

    IdleWatch(long summaryTicks) {
        this.repeats = new RepeatLog(summaryTicks);
    }

    void tick(BoardService service, List<SpiritEntity> spirits, long now) {
        if (spirits.isEmpty()) {
            return;
        }
        Set<UUID> present = new HashSet<>();
        Set<UUID> idle = new HashSet<>();
        for (SpiritEntity spirit : spirits) {
            UUID id = spirit.getUUID();
            present.add(id);
            if (isIdle(service, spirit)) {
                idle.add(id);
            }
        }
        int released = service.board().releaseIdle(present, idle, now);
        if (released <= 0) {
            repeats.clear(CHANNEL);
            return;
        }
        repeats.note(CHANNEL, released, now).ifPresent(summary -> {
            if (summary.count() == 1) {
                LOGGER.info(
                        "[idle] простой духов {}: снято пауз кейсов и блокировок {}, доска снова раздаёт",
                        idle.size(),
                        released);
            } else {
                LOGGER.info(
                        "[idle] сводка: простой повторялся {} раз за окно, снято пауз и блокировок всего {} "
                                + "(за раз {}–{}), доска снова раздаёт",
                        summary.count(),
                        summary.total(),
                        summary.min(),
                        summary.max());
            }
        });
    }

    private static boolean isIdle(BoardService service, SpiritEntity spirit) {
        if (spirit.taskStatus().orElse(null) == RunStatus.RUNNING) {
            return false;
        }
        return service.board().assignedTo(spirit.getUUID()).isEmpty();
    }
}
