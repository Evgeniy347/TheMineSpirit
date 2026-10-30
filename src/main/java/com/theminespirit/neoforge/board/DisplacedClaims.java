package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ClaimGrace;
import com.theminespirit.core.task.SpiritReading;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import org.slf4j.Logger;

final class DisplacedClaims {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Board, Map<String, Mark>> MARKS = new WeakHashMap<>();

    private DisplacedClaims() {}

    static void release(SpiritEntity spirit, BoardTicket ticket, Board board, Map<String, Long> issued) {
        Map<String, Mark> marks = MARKS.computeIfAbsent(board, ignored -> new HashMap<>());
        long now = board.now();
        long generation = issued.getOrDefault(ticket.id(), SpiritReading.UNKNOWN_GENERATION);
        Mark mark = marks.get(ticket.id());
        if (mark == null || mark.generation() != generation) {
            marks.put(ticket.id(), new Mark(generation, now));
            return;
        }
        if (!ClaimGrace.expired(mark.since(), now)) {
            return;
        }
        marks.remove(ticket.id());
        issued.remove(ticket.id());
        if (!board.release(ticket.id())) {
            return;
        }
        LOGGER.info(
                "[board] задание {} освобождено: дух {} занят задачей {}, назначенная часть не начата за {} тиков — "
                        + "в очередь другому",
                ticket.id(),
                spirit.displayName(),
                spirit.currentTaskScript().orElse("-"),
                ClaimGrace.TICKS);
    }

    private record Mark(long generation, long since) {}
}
