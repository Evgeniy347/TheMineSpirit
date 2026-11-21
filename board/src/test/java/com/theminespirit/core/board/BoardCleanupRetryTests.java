package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class BoardCleanupRetryTests {

    @Test
    void failedCleanup_AfterBackoff_IsPostedAgain() {
        for (BoardCase boardCase :
                List.of(BoardCase.FILL_CRATER, BoardCase.DISMANTLE_SHELTER, BoardCase.REPAIR_FENCE)) {
            AtomicLong now = new AtomicLong(0);
            Board board = new Board(now::get);
            BoardTicket ticket = ticket(boardCase);

            assertEquals(
                    BoardOutcome.ACCEPTED,
                    board.submit(ticket),
                    boardCase + ": первая постановка"
                            + " Иначе тест failedCleanup_AfterBackoff_IsPostedAgain перестанет ловить эту поломку. При смене failedCleanup_AfterBackoff_IsPostedAgain проверь это утверждение.");
            assertEquals(
                    BoardOutcome.CLAIMED,
                    board.claim(ticket.id(), UUID.randomUUID(), now.get()),
                    "выдача"
                            + " Иначе тест failedCleanup_AfterBackoff_IsPostedAgain перестанет ловить эту поломку. При смене failedCleanup_AfterBackoff_IsPostedAgain проверь это утверждение.");
            assertEquals(
                    true,
                    board.fail(ticket.id(), new StockView(Map.of()), null),
                    "провал задания"
                            + " Иначе тест failedCleanup_AfterBackoff_IsPostedAgain перестанет ловить эту поломку. При смене failedCleanup_AfterBackoff_IsPostedAgain проверь это утверждение.");

            now.addAndGet(BoardTuning.failBackoffTicks());
            assertEquals(
                    BoardOutcome.ACCEPTED,
                    board.submit(ticket),
                    boardCase + ": причина в мире не исчезла, задание обязано вернуться в выдачу после паузы, "
                            + "иначе снос/засыпка пропадают навсегда («набор уборки не изменился»)"
                            + " При смене failedCleanup_AfterBackoff_IsPostedAgain проверь это утверждение.");
        }
    }

    @Test
    void failedWorkTicket_AfterBackoff_StillWaitsForChangedConditions() {
        AtomicLong now = new AtomicLong(0);
        Board board = new Board(now::get);
        BoardTicket ticket = ticket(BoardCase.GATHER_MATERIALS);

        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(ticket),
                "первая постановка"
                        + " Иначе тест failedWorkTicket_AfterBackoff_StillWaitsForChangedConditions перестанет ловить эту поломку. При смене failedWorkTicket_AfterBackoff_StillWaitsForChangedConditions проверь это утверждение.");
        board.claim(ticket.id(), UUID.randomUUID(), now.get());
        board.fail(ticket.id(), new StockView(Map.of()), null);
        now.addAndGet(BoardTuning.failBackoffTicks());

        assertEquals(
                BoardOutcome.DUPLICATE,
                board.submit(ticket),
                "Обычная работа не повторяется сама: повтор без изменения условий зациклил бы доску"
                        + " Иначе тест failedWorkTicket_AfterBackoff_StillWaitsForChangedConditions перестанет ловить эту поломку. При смене failedWorkTicket_AfterBackoff_StillWaitsForChangedConditions проверь это утверждение.");
    }

    private static BoardTicket ticket(BoardCase boardCase) {
        return new BoardTicket(
                "cleanup:" + boardCase,
                boardCase,
                Optional.empty(),
                BoardRules.priorityOf(boardCase),
                0,
                1,
                "world:test",
                List.of(),
                Map.of());
    }
}
