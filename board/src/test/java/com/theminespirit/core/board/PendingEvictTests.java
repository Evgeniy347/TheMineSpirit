package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PendingEvictTests {
    private static BoardTicket ticket(String id, BoardCase boardCase, int priority) {
        return new BoardTicket(
                id, boardCase, Optional.empty(), priority, 0, 1, BoardTicket.ALWAYS_CAUSE, List.of(), Map.of());
    }

    @Test
    void evictOverflow_RepairOverLimit_EvictsOldestRepairsOnly() {
        Board board = new Board(() -> 0);
        for (int index = 0; index < BoardTuning.maxRepairPending() + 2; index++) {
            board.submit(ticket("repair:step_" + index, BoardCase.REPAIR_BUILDING, index));
        }

        board.prune(0);

        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf("repair:step_0"),
                "Ремонты имеют свой предел: переполнение ремонтов разгружает самые старые repair-билеты, "
                        + "а не оставляет доску расти без границы");
        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf("repair:step_" + (BoardTuning.maxRepairPending() + 1)),
                "Свежий ремонт остаётся: вытесняются только самые старые сверх предела");
        long left = board.all().stream()
                .filter(ticket -> ticket.id().startsWith("repair:"))
                .filter(ticket -> board.statusOf(ticket.id()).orElse(null) == BoardStatus.PENDING)
                .count();
        assertEquals(
                BoardTuning.maxRepairPending(),
                left,
                "Ремонтов на доске ровно столько, сколько разрешает отдельный предел");
    }
}
