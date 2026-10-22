package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardStateTests {

    private static Board boardAt(long now) {
        return new Board(() -> now);
    }

    private static BoardTicket ticket(String id, BoardCase boardCase, String cause) {
        return new BoardTicket(
                id, boardCase, Optional.empty(), BoardRules.priorityOf(boardCase), 0, 1, cause, List.of(), Map.of());
    }

    @Test
    void snapshot_Restore_KeepsTicketsStatusesAndHolders() {
        Board board = boardAt(10);
        UUID holder = UUID.randomUUID();
        board.submit(ticket("first:clear_grounds", BoardCase.CLEAR_GROUNDS, BoardTicket.ALWAYS_CAUSE));
        board.claim("first:clear_grounds", holder, 10);
        board.complete("first:clear_grounds");
        board.submit(ticket("first:stock_chests", BoardCase.STOCK_CHESTS, BoardTicket.ALWAYS_CAUSE));
        board.claim("first:stock_chests", holder, 10);

        Board restored = boardAt(10);
        restored.restore(board.snapshot());

        assertEquals(
                Optional.of(BoardStatus.DONE),
                restored.statusOf("first:clear_grounds"),
                "DONE обязан пережить снимок: иначе выполненный шаг вернётся в очередь после перезапуска");
        assertEquals(
                Optional.of(BoardStatus.ASSIGNED),
                restored.statusOf("first:stock_chests"),
                "Статус выданного задания обязан пережить снимок: после перезапуска доска закрывает его, "
                        + "а не выдаёт заново");
        assertEquals(
                Optional.of(holder),
                restored.holderOf("first:stock_chests"),
                "Держатель задания обязан пережить снимок: без него выданное задание осталось бы без духа");
        assertTrue(
                restored.ledger().has(BoardCase.CLEAR_GROUNDS, 1),
                "Отметка ledger обязана пережить снимок: по ней первые шаги не открывают выполненный кейс");
    }

    @Test
    void restore_DoneFirstStep_ReopenRefused() {
        Board board = boardAt(10);
        UUID holder = UUID.randomUUID();
        board.submit(ticket("first:clear_grounds", BoardCase.CLEAR_GROUNDS, BoardTicket.ALWAYS_CAUSE));
        board.claim("first:clear_grounds", holder, 10);
        board.complete("first:clear_grounds");

        Board restored = boardAt(10);
        restored.restore(board.snapshot());
        BoardOutcome outcome =
                restored.submit(ticket("first:clear_grounds", BoardCase.CLEAR_GROUNDS, BoardTicket.ALWAYS_CAUSE));

        assertEquals(
                BoardOutcome.DUPLICATE,
                outcome,
                "Выполненный шаг первых шагов не переоткрывается и после загрузки снимка: повторная постановка "
                        + "должна быть отклонена");
        assertEquals(
                Optional.of(BoardStatus.DONE),
                restored.statusOf("first:clear_grounds"),
                "После отказа от повторной постановки статус остаётся DONE, а не возвращается в PENDING");
    }

    @Test
    void restore_FailureBackoff_KeepsCooldown() {
        Board board = boardAt(10);
        board.submit(ticket("supply:oak_planks", BoardCase.SUPPLY_SITE, BoardTicket.ALWAYS_CAUSE));
        board.claim("supply:oak_planks", UUID.randomUUID(), 10);
        board.fail("supply:oak_planks");

        Board restored = boardAt(10);
        restored.restore(board.snapshot());

        assertTrue(
                restored.recentlyFailed("supply:oak_planks", 10),
                "Пауза после провала обязана пережить снимок: иначе сразу после перезапуска задание "
                        + "поставится снова без выдержки");
        assertEquals(
                1,
                restored.ticketFailures("supply:oak_planks"),
                "Счётчик провалов задания обязан пережить снимок: по нему считается повторная постановка");
    }

    @Test
    void restore_Empty_LeavesBoardClean() {
        Board board = boardAt(10);
        board.submit(ticket("first:clear_grounds", BoardCase.CLEAR_GROUNDS, BoardTicket.ALWAYS_CAUSE));

        board.restore(BoardState.empty());

        assertFalse(
                board.statusOf("first:clear_grounds").isPresent(),
                "Пустой снимок обязан очистить доску: смешивание старого и нового состояния дало бы "
                        + "задания, которых нет в сохранённом мире");
        assertFalse(
                board.ledger().has(BoardCase.CLEAR_GROUNDS, 1),
                "Пустой снимок не оставляет отметок ledger: иначе выдуманный факт откроет первые шаги");
    }
}
