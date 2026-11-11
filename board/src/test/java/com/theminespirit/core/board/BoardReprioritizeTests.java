package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardReprioritizeTests {
    private static BoardTicket ticket(String id, int priority) {
        return new BoardTicket(
                id, BoardCase.GATHER_MATERIALS, Optional.empty(), priority, 0, 1, "graph:" + id, List.of(), Map.of());
    }

    @Test
    void reprioritize_PendingTicket_QueueOrderFollowsNewPriority() {
        Board board = new Board(() -> 0);
        board.submit(ticket("graph:chop_log", 40));
        board.submit(ticket("graph:dig_dirt", 50));

        boolean changed = board.reprioritize("graph:chop_log", 90);

        assertTrue(changed, "Граф поднял приоритет открытого задания — доска обязана его принять");
        assertEquals(
                "graph:chop_log",
                board.claimable(new StockView(Map.of()), 0).get(0).id(),
                "Поднятое графом задание выдаётся первым: QueueOrder сортирует по новому приоритету");
    }

    @Test
    void reprioritize_SamePriority_NoChange() {
        Board board = new Board(() -> 0);
        board.submit(ticket("graph:chop_log", 40));

        assertFalse(
                board.reprioritize("graph:chop_log", 40),
                "Тот же приоритет — не изменение: иначе каждый тик доски писал бы пустую правку");
    }

    @Test
    void reprioritize_DoneTicket_Ignored() {
        Board board = new Board(() -> 0);
        board.submit(ticket("graph:chop_log", 40));
        board.claim("graph:chop_log", new UUID(0, 1), 0);
        board.complete("graph:chop_log");
        int closed = board.find("graph:chop_log").orElseThrow().priority();

        boolean changed = board.reprioritize("graph:chop_log", 90);

        assertFalse(changed, "Сделанное задание не переоценивается: следующий заход граф поставит его заново");
        assertEquals(
                closed,
                board.find("graph:chop_log").orElseThrow().priority(),
                "Приоритет закрытого задания остаётся историей, а не текущим давлением графа");
    }
}
