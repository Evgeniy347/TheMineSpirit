package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
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

        assertTrue(
                board.reopen("repair:build_house"),
                "DONE-задание переоткрывается: ремонт снова ставит работу"
                        + " Иначе тест reopen_AfterDone_KeepsDoneTotalAndCountsReopenedSeparately перестанет ловить эту поломку. При смене reopen_AfterDone_KeepsDoneTotalAndCountsReopenedSeparately проверь это утверждение.");

        assertEquals(
                1,
                board.ledger().doneTotal(),
                "Счётчик DONE не откатывается при REOPENED: иначе наблюдатель видит 1230→1229, хотя "
                        + "работа не пропадала; REOPENED ведётся отдельно"
                        + " При смене reopen_AfterDone_KeepsDoneTotalAndCountsReopenedSeparately проверь это утверждение.");
        assertEquals(
                1,
                board.ledger().reopenedTotal(),
                "REOPENED учитывается своим счётчиком: по нему видно, сколько выполненного открыто заново"
                        + " Иначе тест reopen_AfterDone_KeepsDoneTotalAndCountsReopenedSeparately перестанет ловить эту поломку. При смене reopen_AfterDone_KeepsDoneTotalAndCountsReopenedSeparately проверь это утверждение.");
    }

    @Test
    void reopen_OrchardSpotGrownAgain_TakesSameId() {
        Board board = new Board(() -> 10);
        BoardTicket harvest = new BoardTicket(
                "orchard:-165,-438",
                BoardCase.HARVEST_ORCHARD,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.HARVEST_ORCHARD),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
        board.submit(harvest);
        board.claim(harvest.id(), SPIRIT, 0);
        board.complete(harvest.id());

        assertTrue(
                board.reopen(harvest.id()),
                "Клетка сада снова с деревом возвращает выполненную рубку: иначе сад зарастает навсегда"
                        + " При смене reopen_OrchardSpotGrownAgain_TakesSameId проверь это утверждение.");
        board.submit(harvest);

        assertEquals(
                BoardStatus.PENDING,
                board.statusOf(harvest.id()).orElse(null),
                "Заявка на ту же клетку сада после переоткрытия снова в доске: id места постоянный"
                        + " Иначе тест reopen_OrchardSpotGrownAgain_TakesSameId перестанет ловить эту поломку. При смене reopen_OrchardSpotGrownAgain_TakesSameId проверь это утверждение.");
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
                        + "а не текущие статусы, поэтому только растёт"
                        + " Иначе тест complete_AfterReopen_GrowsDoneTotalAgain перестанет ловить эту поломку. При смене complete_AfterReopen_GrowsDoneTotalAgain проверь это утверждение.");
        assertEquals(
                1,
                board.ledger().reopenedTotal(),
                "Повторное выполнение не трогает счётчик REOPENED: открытий было ровно одно"
                        + " Иначе тест complete_AfterReopen_GrowsDoneTotalAgain перестанет ловить эту поломку. При смене complete_AfterReopen_GrowsDoneTotalAgain проверь это утверждение.");
    }
}
