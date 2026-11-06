package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(100L, timing.startedAt(), "Начало — тик выдачи из журнала");
        assertEquals(700L, timing.finishedAt(), "Конец — тик успешного завершения");
        assertEquals(600L, timing.durationTicks(999), "Затрата завершённого задания не зависит от текущего тика");
    }

    @Test
    void fromJournal_AssignedWithoutClose_CountsUpToNow() {
        BoardTiming timing = BoardTiming.fromJournal(List.of(event(BoardLog.Event.ASSIGNED, 100)))
                .get("board-1");

        assertTrue(timing.started(), "Выданное задание обязано иметь начало");
        assertFalse(timing.finished(), "Незакрытое задание не имеет конца");
        assertEquals(400L, timing.durationTicks(500), "Затрата в работе растёт до текущего тика");
    }

    @Test
    void fromJournal_ReassignedAfterFailure_StartsFromLastAssignment() {
        BoardTiming timing = BoardTiming.fromJournal(List.of(
                        event(BoardLog.Event.ASSIGNED, 100),
                        event(BoardLog.Event.FAILED, 300),
                        event(BoardLog.Event.ASSIGNED, 400)))
                .get("board-1");

        assertEquals(400L, timing.startedAt(), "Повторная выдача начинает отсчёт заново");
        assertEquals(0L, timing.finishedAt(), "Провал до последней выдачи не закрывает новую работу");
        assertEquals(100L, timing.durationTicks(500), "Затрата считается от последней выдачи");
    }

    @Test
    void fromJournal_CloseOnly_KeepsFinishWithoutDuration() {
        BoardTiming timing = BoardTiming.fromJournal(List.of(event(BoardLog.Event.CANCELLED, 700)))
                .get("board-1");

        assertEquals(0L, timing.startedAt(), "Отмена без выдачи не выдумывает начало");
        assertEquals(700L, timing.finishedAt(), "Отмена закрывает задание временем из журнала");
        assertEquals(0L, timing.durationTicks(999), "Без начала затрата неизвестна и равна нулю");
    }

    @Test
    void fromJournal_EachClosedEvent_StopsTheClock() {
        for (BoardLog.Event closed : List.of(BoardLog.Event.DONE, BoardLog.Event.FAILED, BoardLog.Event.CANCELLED)) {
            BoardTiming timing = BoardTiming.fromJournal(
                            List.of(event(BoardLog.Event.ASSIGNED, 100), event(closed, 700)))
                    .get("board-1");

            assertEquals(700L, timing.finishedAt(), closed + " обязано закрывать задание временем из журнала");
        }
    }

    @Test
    void fromJournal_ReverseOrder_GivesSameTiming() {
        BoardTiming timing = BoardTiming.fromJournal(
                        List.of(event(BoardLog.Event.DONE, 700), event(BoardLog.Event.ASSIGNED, 100)))
                .get("board-1");

        assertEquals(100L, timing.startedAt(), "Порядок записей в хвосте журнала не меняет начало");
        assertEquals(700L, timing.finishedAt(), "Порядок записей в хвосте журнала не меняет конец");
    }

    @Test
    void attempt_PendingAfterPreviousRun_HidesStaleJournalTimes() {
        BoardTiming attempt = BoardTiming.attempt(BoardStatus.PENDING, new BoardTiming(100L, 700L), false, 50L);

        assertFalse(
                attempt.started(), "Задание в очереди ещё не начато: старое начало прошлой попытки не показывается");
        assertFalse(attempt.finished(), "Задание в очереди не закончено: старое закрытие прошлой попытки не видно");
    }

    @Test
    void attempt_AssignedWithOldFinish_DropsFinishAndCountsToNow() {
        BoardTiming attempt = BoardTiming.attempt(BoardStatus.ASSIGNED, new BoardTiming(100L, 200L), false, 100L);

        assertEquals(100L, attempt.startedAt(), "Начало идущего задания — последняя выдача из журнала");
        assertFalse(attempt.finished(), "Идущее задание не имеет конца, даже если журнал помнит старый провал");
        assertEquals(300L, attempt.durationTicks(400L), "Идущее задание тратит время до текущего тика");
    }

    @Test
    void attempt_ClosedStatus_KeepsFinishForSpentTime() {
        for (BoardStatus status : List.of(BoardStatus.DONE, BoardStatus.FAILED, BoardStatus.CANCELLED)) {
            BoardTiming attempt = BoardTiming.attempt(status, new BoardTiming(100L, 700L), false, 100L);

            assertEquals(700L, attempt.finishedAt(), status + " обязано показывать конец текущей попытки");
            assertEquals(600L, attempt.durationTicks(999L), status + " обязано показывать затрату закрытой попытки");
        }
    }

    @Test
    void attempt_ActivityWithoutJournal_StartsWhenSpiritTookIt() {
        BoardTiming attempt = BoardTiming.attempt(BoardStatus.ASSIGNED, BoardTiming.NONE, true, 5000L);

        assertEquals(5000L, attempt.startedAt(), "Без записи журнала занятие духа началось, когда дух его взял");
        assertFalse(attempt.finished(), "Текущее занятие духа не имеет конца");
        assertEquals(4000L, attempt.durationTicks(9000L), "Длительность занятия считается от начала занятия");
    }

    @Test
    void attempt_ActivityWithJournal_UsesJournalStart() {
        BoardTiming attempt = BoardTiming.attempt(BoardStatus.ASSIGNED, new BoardTiming(300L, 0L), true, 5000L);

        assertEquals(300L, attempt.startedAt(), "Начало занятия берётся из журнала, а не из постановки плитки");
    }
}
