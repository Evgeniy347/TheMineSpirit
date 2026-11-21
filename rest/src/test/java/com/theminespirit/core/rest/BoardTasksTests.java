package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTiming;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.api.BoardCase;
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
                "Задание с нехваткой обязано нести статус ожидания ресурсов: наблюдатель видит, чего ждёт доска"
                        + " Иначе тест of_WaitingTicket_ReportsMissingFromStock перестанет ловить эту поломку. При смене of_WaitingTicket_ReportsMissingFromStock проверь это утверждение.");
        assertEquals(
                Map.of("dirt", 5),
                task.missing(),
                "Наблюдение обязано показывать нехватку, а не полную нужду: стратегу важно, сколько добыть"
                        + " Иначе тест of_WaitingTicket_ReportsMissingFromStock перестанет ловить эту поломку. При смене of_WaitingTicket_ReportsMissingFromStock проверь это утверждение.");
    }

    @Test
    void of_GrassInStock_DirtNeedCoveredAndStockShown() {
        Board board = new Board(() -> 0);
        BoardTicket ticket = ticket("repair:hut", Map.of("minecraft:dirt", 528));
        board.submit(ticket);

        BoardTask task = BoardTasks.of(board, ticket, new StockView(Map.of("minecraft:grass_block", 1575)));

        assertEquals(
                Map.of(),
                task.missing(),
                "Нужду в земле закрывает дёрн: задание не ждёт добычи, и карточка не показывает нехватку"
                        + " Иначе тест of_GrassInStock_DirtNeedCoveredAndStockShown перестанет ловить эту поломку. При смене of_GrassInStock_DirtNeedCoveredAndStockShown проверь это утверждение.");
        assertEquals(
                Map.of("minecraft:dirt", 1575),
                task.stock(),
                "Карточка несёт склад по нужным предметам: наблюдатель видит «есть 1575 / надо 528»"
                        + " Иначе тест of_GrassInStock_DirtNeedCoveredAndStockShown перестанет ловить эту поломку. При смене of_GrassInStock_DirtNeedCoveredAndStockShown проверь это утверждение.");
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
                "После выдачи наблюдатель обязан видеть духа-держателя, а не «любой свободный»"
                        + " Иначе тест of_ClaimedTicket_ReportsHolderForObserver перестанет ловить эту поломку. При смене of_ClaimedTicket_ReportsHolderForObserver проверь это утверждение.");
        assertEquals(
                "ASSIGNED",
                task.status(),
                "Статус выданного задания — ASSIGNED"
                        + " Иначе тест of_ClaimedTicket_ReportsHolderForObserver перестанет ловить эту поломку. При смене of_ClaimedTicket_ReportsHolderForObserver проверь это утверждение.");
    }

    @Test
    void of_BlockedTicketWithLedgerMark_ReportsNotDone() {
        Board board = new Board(() -> 0);
        BoardTicket ticket = ticket("supply_gather:oak_planks", Map.of());
        board.submit(ticket);
        board.ledger().note(BoardCase.SUPPLY_SITE, 1);

        BoardTask task = BoardTasks.of(board, ticket, BoardStatus.BLOCKED, new StockView(Map.of()));

        assertFalse(
                task.done(),
                "Заблокированное задание не выполнено, даже если в журнале есть отметка прошлого прогона: "
                        + "status BLOCKED и done=true противоречат друг другу (аномалия 7)"
                        + " Иначе тест of_BlockedTicketWithLedgerMark_ReportsNotDone перестанет ловить эту поломку. При смене of_BlockedTicketWithLedgerMark_ReportsNotDone проверь это утверждение.");
    }

    @Test
    void of_FreeTicket_HasNoAssignee() {
        Board board = new Board(() -> 0);
        BoardTicket ticket = ticket("supply:dirt", Map.of());
        board.submit(ticket);

        BoardTask task = BoardTasks.of(board, ticket, new StockView(Map.of()));

        assertNull(
                task.assignee(),
                "Невыданное задание без владельца в заявке не имеет держателя: assignee пуст"
                        + " Иначе тест of_FreeTicket_HasNoAssignee перестанет ловить эту поломку. При смене of_FreeTicket_HasNoAssignee проверь это утверждение.");
        assertEquals(
                "PENDING",
                task.status(),
                "Задание без нужды в ресурсах ждёт выдачи со статусом PENDING"
                        + " Иначе тест of_FreeTicket_HasNoAssignee перестанет ловить эту поломку. При смене of_FreeTicket_HasNoAssignee проверь это утверждение.");
    }

    @Test
    void of_TimingFromJournal_FillsStartFinishAndSpent() {
        Board board = new Board(() -> 2000);
        BoardTicket ticket = ticket("supply:dirt", Map.of());
        board.submit(ticket);
        board.claim("supply:dirt", SPIRIT, 100);

        BoardTask running =
                BoardTasks.of(board, ticket, BoardStatus.ASSIGNED, new StockView(Map.of()), new BoardTiming(100L, 0L));

        assertEquals(
                100L,
                running.startedAt(),
                "Наблюдатель обязан видеть тик выдачи из журнала"
                        + " Иначе тест of_TimingFromJournal_FillsStartFinishAndSpent перестанет ловить эту поломку. При смене of_TimingFromJournal_FillsStartFinishAndSpent проверь это утверждение.");
        assertEquals(
                0L,
                running.finishedAt(),
                "Незакрытое задание не имеет конца"
                        + " Иначе тест of_TimingFromJournal_FillsStartFinishAndSpent перестанет ловить эту поломку. При смене of_TimingFromJournal_FillsStartFinishAndSpent проверь это утверждение.");
        assertEquals(
                1900L,
                running.durationTicks(),
                "Затрата в работе считается до текущего тика доски"
                        + " Иначе тест of_TimingFromJournal_FillsStartFinishAndSpent перестанет ловить эту поломку. При смене of_TimingFromJournal_FillsStartFinishAndSpent проверь это утверждение.");

        BoardTask closed =
                BoardTasks.of(board, ticket, BoardStatus.DONE, new StockView(Map.of()), new BoardTiming(100L, 1500L));

        assertEquals(
                1500L,
                closed.finishedAt(),
                "Наблюдатель обязан видеть тик закрытия из журнала"
                        + " Иначе тест of_TimingFromJournal_FillsStartFinishAndSpent перестанет ловить эту поломку. При смене of_TimingFromJournal_FillsStartFinishAndSpent проверь это утверждение.");
        assertEquals(
                1400L,
                closed.durationTicks(),
                "Затрата завершённого задания — разница начала и конца"
                        + " Иначе тест of_TimingFromJournal_FillsStartFinishAndSpent перестанет ловить эту поломку. При смене of_TimingFromJournal_FillsStartFinishAndSpent проверь это утверждение.");
    }

    @Test
    void of_PendingAfterPreviousRun_HidesOldAttemptTimes() {
        Board board = new Board(() -> 2000);
        BoardTicket ticket = ticket("supply:dirt", Map.of());

        BoardTask task =
                BoardTasks.of(board, ticket, BoardStatus.PENDING, new StockView(Map.of()), new BoardTiming(100L, 700L));

        assertEquals(
                0L,
                task.startedAt(),
                "Задание, вернувшееся в очередь, не показывает начало старой попытки"
                        + " Иначе тест of_PendingAfterPreviousRun_HidesOldAttemptTimes перестанет ловить эту поломку. При смене of_PendingAfterPreviousRun_HidesOldAttemptTimes проверь это утверждение.");
        assertEquals(
                0L,
                task.finishedAt(),
                "Задание в очереди не показывает конец старой попытки"
                        + " Иначе тест of_PendingAfterPreviousRun_HidesOldAttemptTimes перестанет ловить эту поломку. При смене of_PendingAfterPreviousRun_HidesOldAttemptTimes проверь это утверждение.");
        assertEquals(
                0L,
                task.durationTicks(),
                "Без начала затрата неизвестна и равна нулю"
                        + " Иначе тест of_PendingAfterPreviousRun_HidesOldAttemptTimes перестанет ловить эту поломку. При смене of_PendingAfterPreviousRun_HidesOldAttemptTimes проверь это утверждение.");
    }

    @Test
    void of_AssignedAfterJournalClose_DropsFinishAndCountsToNow() {
        Board board = new Board(() -> 2000);
        BoardTicket ticket = ticket("supply:dirt", Map.of());

        BoardTask task = BoardTasks.of(
                board, ticket, BoardStatus.ASSIGNED, new StockView(Map.of()), new BoardTiming(100L, 700L));

        assertEquals(
                100L,
                task.startedAt(),
                "Начало берётся из последней выдачи"
                        + " Иначе тест of_AssignedAfterJournalClose_DropsFinishAndCountsToNow перестанет ловить эту поломку. При смене of_AssignedAfterJournalClose_DropsFinishAndCountsToNow проверь это утверждение.");
        assertEquals(
                0L,
                task.finishedAt(),
                "Идущее задание не имеет конца"
                        + " Иначе тест of_AssignedAfterJournalClose_DropsFinishAndCountsToNow перестанет ловить эту поломку. При смене of_AssignedAfterJournalClose_DropsFinishAndCountsToNow проверь это утверждение.");
        assertEquals(
                1900L,
                task.durationTicks(),
                "Идущее задание тратит время до текущего тика"
                        + " Иначе тест of_AssignedAfterJournalClose_DropsFinishAndCountsToNow перестанет ловить эту поломку. При смене of_AssignedAfterJournalClose_DropsFinishAndCountsToNow проверь это утверждение.");
    }

    @Test
    void of_ActivityWithoutIssue_HidesStartUntilJournal() {
        Board board = new Board(() -> 9000);
        BoardTicket activity = new BoardTicket(
                "activity:" + SPIRIT,
                BoardCase.ACTIVITY_MINE,
                Optional.of(SPIRIT),
                BoardRules.priorityOf(BoardCase.ACTIVITY_MINE),
                5000L,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());

        BoardTask task =
                BoardTasks.of(board, activity, BoardStatus.ASSIGNED, new StockView(Map.of()), BoardTiming.NONE);

        assertEquals(
                0L,
                task.startedAt(),
                "Занятие — обычное задание доски: без записи выдачи время начала не выдумывается из постановки"
                        + " Иначе тест of_ActivityWithoutIssue_HidesStartUntilJournal перестанет ловить эту поломку. При смене of_ActivityWithoutIssue_HidesStartUntilJournal проверь это утверждение.");
        assertEquals(
                0L,
                task.finishedAt(),
                "Текущее занятие духа не имеет конца"
                        + " Иначе тест of_ActivityWithoutIssue_HidesStartUntilJournal перестанет ловить эту поломку. При смене of_ActivityWithoutIssue_HidesStartUntilJournal проверь это утверждение.");
        assertEquals(
                0L,
                task.durationTicks(),
                "Длительность занятия считается от журнальной выдачи"
                        + " Иначе тест of_ActivityWithoutIssue_HidesStartUntilJournal перестанет ловить эту поломку. При смене of_ActivityWithoutIssue_HidesStartUntilJournal проверь это утверждение.");
    }

    @Test
    void of_ActivityWithJournal_UsesJournalStart() {
        Board board = new Board(() -> 9000);
        BoardTicket activity = new BoardTicket(
                "activity:" + SPIRIT,
                BoardCase.ACTIVITY_UNLOAD,
                Optional.of(SPIRIT),
                BoardRules.priorityOf(BoardCase.ACTIVITY_UNLOAD),
                5000L,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());

        BoardTask task = BoardTasks.of(
                board, activity, BoardStatus.ASSIGNED, new StockView(Map.of()), new BoardTiming(7000L, 0L));

        assertEquals(
                7000L,
                task.startedAt(),
                "Начало занятия берётся из журнала выдачи"
                        + " Иначе тест of_ActivityWithJournal_UsesJournalStart перестанет ловить эту поломку. При смене of_ActivityWithJournal_UsesJournalStart проверь это утверждение.");
        assertEquals(
                2000L,
                task.durationTicks(),
                "Длительность занятия считается от журнального начала"
                        + " Иначе тест of_ActivityWithJournal_UsesJournalStart перестанет ловить эту поломку. При смене of_ActivityWithJournal_UsesJournalStart проверь это утверждение.");
    }
}
