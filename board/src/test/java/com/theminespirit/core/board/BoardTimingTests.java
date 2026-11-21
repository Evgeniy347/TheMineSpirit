package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardTimingTests {

    private static BoardLogEntry event(BoardLog.Event event, long tick) {
        return new BoardLogEntry(
                event, tick, "board-1", BoardCase.SUPPLY_SITE, 1, "always", 1, 1, List.of(), null, "hash", tick);
    }

    @Test
    void fromJournal_AssignedThenDone_MeasuresWholeRun() {
        Map<String, BoardTiming> timings = BoardTiming.fromJournal(List.of(
                event(BoardLog.Event.POSTED, 10),
                event(BoardLog.Event.ASSIGNED, 100),
                event(BoardLog.Event.DONE, 700)));

        BoardTiming timing = timings.get("board-1");
        assertEquals(
                100L,
                timing.startedAt(),
                "Начало — тик выдачи из журнала"
                        + " Иначе тест fromJournal_AssignedThenDone_MeasuresWholeRun перестанет ловить эту поломку. При смене fromJournal_AssignedThenDone_MeasuresWholeRun проверь это утверждение.");
        assertEquals(
                700L,
                timing.finishedAt(),
                "Конец — тик успешного завершения"
                        + " Иначе тест fromJournal_AssignedThenDone_MeasuresWholeRun перестанет ловить эту поломку. При смене fromJournal_AssignedThenDone_MeasuresWholeRun проверь это утверждение.");
        assertEquals(
                600L,
                timing.durationTicks(999),
                "Затрата завершённого задания не зависит от текущего тика"
                        + " Иначе тест fromJournal_AssignedThenDone_MeasuresWholeRun перестанет ловить эту поломку. При смене fromJournal_AssignedThenDone_MeasuresWholeRun проверь это утверждение.");
    }

    @Test
    void fromJournal_AssignedWithoutClose_CountsUpToNow() {
        BoardTiming timing = BoardTiming.fromJournal(List.of(event(BoardLog.Event.ASSIGNED, 100)))
                .get("board-1");

        assertTrue(
                timing.started(),
                "Выданное задание обязано иметь начало"
                        + " Иначе тест fromJournal_AssignedWithoutClose_CountsUpToNow перестанет ловить эту поломку. При смене fromJournal_AssignedWithoutClose_CountsUpToNow проверь это утверждение.");
        assertFalse(
                timing.finished(),
                "Незакрытое задание не имеет конца"
                        + " Иначе тест fromJournal_AssignedWithoutClose_CountsUpToNow перестанет ловить эту поломку. При смене fromJournal_AssignedWithoutClose_CountsUpToNow проверь это утверждение.");
        assertEquals(
                400L,
                timing.durationTicks(500),
                "Затрата в работе растёт до текущего тика"
                        + " Иначе тест fromJournal_AssignedWithoutClose_CountsUpToNow перестанет ловить эту поломку. При смене fromJournal_AssignedWithoutClose_CountsUpToNow проверь это утверждение.");
    }

    @Test
    void fromJournal_ReassignedAfterFailure_StartsFromLastAssignment() {
        BoardTiming timing = BoardTiming.fromJournal(List.of(
                        event(BoardLog.Event.ASSIGNED, 100),
                        event(BoardLog.Event.FAILED, 300),
                        event(BoardLog.Event.ASSIGNED, 400)))
                .get("board-1");

        assertEquals(
                400L,
                timing.startedAt(),
                "Повторная выдача начинает отсчёт заново"
                        + " Иначе тест fromJournal_ReassignedAfterFailure_StartsFromLastAssignment перестанет ловить эту поломку. При смене fromJournal_ReassignedAfterFailure_StartsFromLastAssignment проверь это утверждение.");
        assertEquals(
                0L,
                timing.finishedAt(),
                "Провал до последней выдачи не закрывает новую работу"
                        + " Иначе тест fromJournal_ReassignedAfterFailure_StartsFromLastAssignment перестанет ловить эту поломку. При смене fromJournal_ReassignedAfterFailure_StartsFromLastAssignment проверь это утверждение.");
        assertEquals(
                100L,
                timing.durationTicks(500),
                "Затрата считается от последней выдачи"
                        + " Иначе тест fromJournal_ReassignedAfterFailure_StartsFromLastAssignment перестанет ловить эту поломку. При смене fromJournal_ReassignedAfterFailure_StartsFromLastAssignment проверь это утверждение.");
    }

    @Test
    void fromJournal_CloseOnly_KeepsFinishWithoutDuration() {
        BoardTiming timing = BoardTiming.fromJournal(List.of(event(BoardLog.Event.CANCELLED, 700)))
                .get("board-1");

        assertEquals(
                0L,
                timing.startedAt(),
                "Отмена без выдачи не выдумывает начало"
                        + " Иначе тест fromJournal_CloseOnly_KeepsFinishWithoutDuration перестанет ловить эту поломку. При смене fromJournal_CloseOnly_KeepsFinishWithoutDuration проверь это утверждение.");
        assertEquals(
                700L,
                timing.finishedAt(),
                "Отмена закрывает задание временем из журнала"
                        + " Иначе тест fromJournal_CloseOnly_KeepsFinishWithoutDuration перестанет ловить эту поломку. При смене fromJournal_CloseOnly_KeepsFinishWithoutDuration проверь это утверждение.");
        assertEquals(
                0L,
                timing.durationTicks(999),
                "Без начала затрата неизвестна и равна нулю"
                        + " Иначе тест fromJournal_CloseOnly_KeepsFinishWithoutDuration перестанет ловить эту поломку. При смене fromJournal_CloseOnly_KeepsFinishWithoutDuration проверь это утверждение.");
    }

    @Test
    void fromJournal_EachClosedEvent_StopsTheClock() {
        for (BoardLog.Event closed : List.of(BoardLog.Event.DONE, BoardLog.Event.FAILED, BoardLog.Event.CANCELLED)) {
            BoardTiming timing = BoardTiming.fromJournal(
                            List.of(event(BoardLog.Event.ASSIGNED, 100), event(closed, 700)))
                    .get("board-1");

            assertEquals(
                    700L,
                    timing.finishedAt(),
                    closed + " обязано закрывать задание временем из журнала"
                            + " Иначе тест fromJournal_EachClosedEvent_StopsTheClock перестанет ловить эту поломку. При смене fromJournal_EachClosedEvent_StopsTheClock проверь это утверждение.");
        }
    }

    @Test
    void fromJournal_ReverseOrder_GivesSameTiming() {
        BoardTiming timing = BoardTiming.fromJournal(
                        List.of(event(BoardLog.Event.DONE, 700), event(BoardLog.Event.ASSIGNED, 100)))
                .get("board-1");

        assertEquals(
                100L,
                timing.startedAt(),
                "Порядок записей в хвосте журнала не меняет начало"
                        + " Иначе тест fromJournal_ReverseOrder_GivesSameTiming перестанет ловить эту поломку. При смене fromJournal_ReverseOrder_GivesSameTiming проверь это утверждение.");
        assertEquals(
                700L,
                timing.finishedAt(),
                "Порядок записей в хвосте журнала не меняет конец"
                        + " Иначе тест fromJournal_ReverseOrder_GivesSameTiming перестанет ловить эту поломку. При смене fromJournal_ReverseOrder_GivesSameTiming проверь это утверждение.");
    }

    @Test
    void attempt_PendingAfterPreviousRun_HidesStaleJournalTimes() {
        BoardTiming attempt = BoardTiming.attempt(BoardStatus.PENDING, new BoardTiming(100L, 700L));

        assertFalse(
                attempt.started(),
                "Задание в очереди ещё не начато: старое начало прошлой попытки не показывается"
                        + " Иначе тест attempt_PendingAfterPreviousRun_HidesStaleJournalTimes перестанет ловить эту поломку. При смене attempt_PendingAfterPreviousRun_HidesStaleJournalTimes проверь это утверждение.");
        assertFalse(
                attempt.finished(),
                "Задание в очереди не закончено: старое закрытие прошлой попытки не видно"
                        + " Иначе тест attempt_PendingAfterPreviousRun_HidesStaleJournalTimes перестанет ловить эту поломку. При смене attempt_PendingAfterPreviousRun_HidesStaleJournalTimes проверь это утверждение.");
    }

    @Test
    void attempt_AssignedWithOldFinish_DropsFinishAndCountsToNow() {
        BoardTiming attempt = BoardTiming.attempt(BoardStatus.ASSIGNED, new BoardTiming(100L, 200L));

        assertEquals(
                100L,
                attempt.startedAt(),
                "Начало идущего задания — последняя выдача из журнала"
                        + " Иначе тест attempt_AssignedWithOldFinish_DropsFinishAndCountsToNow перестанет ловить эту поломку. При смене attempt_AssignedWithOldFinish_DropsFinishAndCountsToNow проверь это утверждение.");
        assertFalse(
                attempt.finished(),
                "Идущее задание не имеет конца, даже если журнал помнит старый провал"
                        + " Иначе тест attempt_AssignedWithOldFinish_DropsFinishAndCountsToNow перестанет ловить эту поломку. При смене attempt_AssignedWithOldFinish_DropsFinishAndCountsToNow проверь это утверждение.");
        assertEquals(
                300L,
                attempt.durationTicks(400L),
                "Идущее задание тратит время до текущего тика"
                        + " Иначе тест attempt_AssignedWithOldFinish_DropsFinishAndCountsToNow перестанет ловить эту поломку. При смене attempt_AssignedWithOldFinish_DropsFinishAndCountsToNow проверь это утверждение.");
    }

    @Test
    void attempt_ClosedStatus_KeepsFinishForSpentTime() {
        for (BoardStatus status : List.of(BoardStatus.DONE, BoardStatus.FAILED, BoardStatus.CANCELLED)) {
            BoardTiming attempt = BoardTiming.attempt(status, new BoardTiming(100L, 700L));

            assertEquals(
                    700L,
                    attempt.finishedAt(),
                    status + " обязано показывать конец текущей попытки"
                            + " Иначе тест attempt_ClosedStatus_KeepsFinishForSpentTime перестанет ловить эту поломку. При смене attempt_ClosedStatus_KeepsFinishForSpentTime проверь это утверждение.");
            assertEquals(
                    600L,
                    attempt.durationTicks(999L),
                    status + " обязано показывать затрату закрытой попытки"
                            + " Иначе тест attempt_ClosedStatus_KeepsFinishForSpentTime перестанет ловить эту поломку. При смене attempt_ClosedStatus_KeepsFinishForSpentTime проверь это утверждение.");
        }
    }

    @Test
    void attempt_ActivityWithJournal_UsesJournalStart() {
        BoardTiming attempt = BoardTiming.attempt(BoardStatus.ASSIGNED, new BoardTiming(300L, 0L));

        assertEquals(
                300L,
                attempt.startedAt(),
                "Занятие — обычное задание доски: начало берётся из журнала выдачи, а не из постановки плитки"
                        + " Иначе тест attempt_ActivityWithJournal_UsesJournalStart перестанет ловить эту поломку. При смене attempt_ActivityWithJournal_UsesJournalStart проверь это утверждение.");
        assertEquals(
                700L,
                attempt.durationTicks(1000L),
                "Длительность занятия считается от выдачи журналом"
                        + " Иначе тест attempt_ActivityWithJournal_UsesJournalStart перестанет ловить эту поломку. При смене attempt_ActivityWithJournal_UsesJournalStart проверь это утверждение.");
    }
}
