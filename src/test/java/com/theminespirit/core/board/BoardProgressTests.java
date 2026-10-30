package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardProgressTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static BoardTicket ticket(String id) {
        return new BoardTicket(
                id,
                BoardCase.BUILD_HOUSE,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.BUILD_HOUSE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }

    private static void finish(Board board, String id) {
        board.submit(ticket(id));
        board.claim(id, SPIRIT, 0);
        board.complete(id);
    }

    @Test
    void reopen_AfterDone_KeepsDoneTotalAndCountsReopenedSeparately() {
        Board board = new Board(() -> 10);
        finish(board, "repair:build_house");

        assertTrue(board.reopen("repair:build_house"), "DONE-задание переоткрывается: ремонт снова ставит работу");

        assertEquals(
                1,
                board.ledger().doneTotal(),
                "Счётчик DONE не откатывается при REOPENED: иначе наблюдатель видит 1230→1229, хотя "
                        + "работа не пропадала; REOPENED ведётся отдельно");
        assertEquals(
                1,
                board.ledger().reopenedTotal(),
                "REOPENED учитывается своим счётчиком: по нему видно, сколько выполненного открыто заново");
    }

    @Test
    void complete_AfterReopen_GrowsDoneTotalAgain() {
        Board board = new Board(() -> 10);
        finish(board, "repair:build_house");
        board.reopen("repair:build_house");

        finish(board, "repair:build_house");

        assertEquals(
                2,
                board.ledger().doneTotal(),
                "Повторное выполнение после ремонта добавляет DONE: кумулятивный счётчик считает переходы, "
                        + "а не текущие статусы, поэтому только растёт");
        assertEquals(
                1,
                board.ledger().reopenedTotal(),
                "Повторное выполнение не трогает счётчик REOPENED: открытий было ровно одно");
    }
}
