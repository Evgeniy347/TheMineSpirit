package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTiming;
import com.theminespirit.core.board.StockView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardTasksTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static BoardTicket ticket(String id, Map<String, Integer> required) {
        return new BoardTicket(
                id,
                BoardCase.SUPPLY_SITE,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.SUPPLY_SITE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                required);
    }

    @Test
    void of_WaitingTicket_ReportsMissingFromStock() {
        Board board = new Board(() -> 0);
        BoardTicket ticket = ticket("supply:dirt", Map.of("dirt", 8));
        board.submit(ticket);
        board.claimable(new StockView(Map.of("dirt", 3)), 0);

        BoardTask task = BoardTasks.of(board, ticket, new StockView(Map.of("dirt", 3)));

        assertEquals(
                "WAITING_RESOURCES",
                task.status(),
                "Задание с нехваткой обязано нести статус ожидания ресурсов: наблюдатель видит, чего ждёт доска");
        assertEquals(
                Map.of("dirt", 5),
                task.missing(),
                "Наблюдение обязано показывать нехватку, а не полную нужду: стратегу важно, сколько добыть");
    }

    @Test
    void of_ClaimedTicket_ReportsHolderForObserver() {
        Board board = new Board(() -> 0);
        BoardTicket ticket = ticket("supply:dirt", Map.of());
        board.submit(ticket);
        board.claim("supply:dirt", SPIRIT, 0);

        BoardTask task = BoardTasks.of(board, ticket, new StockView(Map.of()));

        assertEquals(
                SPIRIT.toString(),
                task.assignee(),
                "После выдачи наблюдатель обязан видеть духа-держателя, а не «любой свободный»");
        assertEquals("ASSIGNED", task.status(), "Статус выданного задания — ASSIGNED");
    }

    @Test
    void of_FreeTicket_HasNoAssignee() {
        Board board = new Board(() -> 0);
        BoardTicket ticket = ticket("supply:dirt", Map.of());
        board.submit(ticket);

        BoardTask task = BoardTasks.of(board, ticket, new StockView(Map.of()));

        assertNull(task.assignee(), "Невыданное задание без владельца в заявке не имеет держателя: assignee пуст");
        assertEquals("PENDING", task.status(), "Задание без нужды в ресурсах ждёт выдачи со статусом PENDING");
    }

    @Test
    void of_TimingFromJournal_FillsStartFinishAndSpent() {
        Board board = new Board(() -> 2000);
        BoardTicket ticket = ticket("supply:dirt", Map.of());
        board.submit(ticket);
        board.claim("supply:dirt", SPIRIT, 100);

        BoardTask running =
                BoardTasks.of(board, ticket, BoardStatus.ASSIGNED, new StockView(Map.of()), new BoardTiming(100L, 0L));

        assertEquals(100L, running.startedAt(), "Наблюдатель обязан видеть тик выдачи из журнала");
        assertEquals(0L, running.finishedAt(), "Незакрытое задание не имеет конца");
        assertEquals(1900L, running.durationTicks(), "Затрата в работе считается до текущего тика доски");

        BoardTask closed =
                BoardTasks.of(board, ticket, BoardStatus.DONE, new StockView(Map.of()), new BoardTiming(100L, 1500L));

        assertEquals(1500L, closed.finishedAt(), "Наблюдатель обязан видеть тик закрытия из журнала");
        assertEquals(1400L, closed.durationTicks(), "Затрата завершённого задания — разница начала и конца");
    }
}
