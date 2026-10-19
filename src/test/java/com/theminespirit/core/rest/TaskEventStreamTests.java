package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.engine.RunProgress;
import com.theminespirit.core.task.TaskEvent;
import com.theminespirit.core.task.TaskEventLog;
import com.theminespirit.core.task.TaskRecord;
import com.theminespirit.core.task.TaskRegistry;
import com.theminespirit.core.task.TaskState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TaskEventStreamTests {
    private static final TaskRecord RUNNING = new TaskRecord(
            "t-1", "bot", TaskState.RUNNING, "цель", new RunProgress(2, 3, "goto", null, null), null, null);
    private static final Duration NO_WAIT = Duration.ofMillis(60);

    private static List<TaskEvent> events(String type, int count) {
        List<TaskEvent> batch = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            batch.add(new TaskEvent(0, type, Map.of("n", i)));
        }
        return batch;
    }

    private static TaskEventStream fresh(TaskRecord record, TaskEventLog log, long lastEventId, AtomicInteger closes) {
        TaskRegistry.Snapshot snapshot =
                new TaskRegistry.Snapshot(record, log, log.since(lastEventId), log.lastId(), lastEventId);
        return new TaskEventStream(snapshot, NO_WAIT, closes::incrementAndGet);
    }

    @Test
    void nextFrame_FreshConnection_EmitsCurrentStateAtLogPosition() throws Exception {
        TaskEventLog log = new TaskEventLog();
        log.append(events(TaskEvent.TYPE_PROGRESS, 2));

        try (TaskEventStream stream = fresh(RUNNING, log, 0, new AtomicInteger())) {
            String frame = stream.nextFrame();

            assertTrue(frame.startsWith("id: 2\n"), "Свежий кадр обязан стоять на позиции журнала: " + frame);
            assertTrue(frame.contains("event: state"), "Первый кадр свежего подключения обязан быть state: " + frame);
            assertTrue(frame.contains("\"state\":\"running\""), "Кадр обязан нести текущее состояние: " + frame);
            assertTrue(
                    frame.contains("\"current_script\":\"goto\""),
                    "Кадр state обязан нести текущий прогресс: " + frame);
        }
    }

    @Test
    void nextFrame_FreshConnection_SkipsHistoryAndEndsOnClosedLog() throws Exception {
        TaskEventLog log = new TaskEventLog();
        log.append(events(TaskEvent.TYPE_PROGRESS, 3));
        log.close();

        try (TaskEventStream stream = fresh(RUNNING, log, 0, new AtomicInteger())) {
            String state = stream.nextFrame();
            assertTrue(state.contains("event: state"), "Свежий поток обязан начинаться с кадром state: " + state);
            assertNull(stream.nextFrame(), "Закрытый журнал обязан завершать поток сразу после state");
        }
    }

    @Test
    void nextFrame_ReconnectWithLastEventId_DeliversOnlyLaterFrames() throws Exception {
        TaskEventLog log = new TaskEventLog();
        log.append(events(TaskEvent.TYPE_PROGRESS, 2));

        try (TaskEventStream stream = fresh(RUNNING, log, 1, new AtomicInteger())) {
            String frame = stream.nextFrame();
            assertTrue(
                    frame.startsWith("id: 2\n"),
                    "После Last-Event-ID=1 обязана прийти только более поздняя запись: " + frame);
            log.close();
            assertNull(stream.nextFrame(), "Догнав журнал, поток обязан завершиться");
        }
    }

    @Test
    void nextFrame_LastEventIdAheadOfLog_EmitsStateAtLogPositionAndContinuesWithNewEvents() throws Exception {
        TaskEventLog log = new TaskEventLog();
        log.append(events(TaskEvent.TYPE_PROGRESS, 2));

        try (TaskEventStream stream = fresh(RUNNING, log, 50, new AtomicInteger())) {
            String state = stream.nextFrame();
            assertTrue(
                    state.startsWith("id: 2\n") && state.contains("event: state"),
                    "Клиент с Last-Event-ID впереди журнала обязан получить текущий state на позиции журнала (id 2), "
                            + "а не ждать события с несуществующим id: " + state);

            log.append(events(TaskEvent.TYPE_PROGRESS, 1));
            String next = stream.nextFrame();
            assertTrue(
                    next.startsWith("id: 3\n") && next.contains("event: progress"),
                    "После state поток обязан продолжаться с позиции журнала и отдать новое событие id 3, а не "
                            + "ждать id > 50 и слать одни кипалайвы: " + next);
        }
    }

    @Test
    void nextFrame_ReconnectAfterGap_EmitsStateThenRemainingEvents() throws Exception {
        TaskEventLog log = new TaskEventLog();
        List<TaskEvent> bulk = new ArrayList<>();
        for (int i = 0; i < TaskEventLog.CAPACITY + 40; i++) {
            bulk.add(new TaskEvent(0, TaskEvent.TYPE_PROGRESS, Map.of("n", i)));
        }
        log.append(bulk);
        long newest = log.lastId();

        try (TaskEventStream stream = fresh(RUNNING, log, 1, new AtomicInteger())) {
            String state = stream.nextFrame();
            assertTrue(state.contains("event: state"), "При потере окна обязана прийти текущая state: " + state);
            long stateId = Long.parseLong(state.split("\n", 2)[0].substring(4));
            String later = stream.nextFrame();
            assertTrue(later.startsWith("id: "), "После state обязаны идти оставшиеся события журнала: " + later);
            long id = Long.parseLong(later.split("\n", 2)[0].substring(4));
            assertTrue(id <= newest, "Догоняющие события не могут быть новее позиции журнала: " + id);
            assertTrue(id > 1, "Догонять обязаны записи новее Last-Event-ID: " + id);
            assertTrue(
                    stateId < id,
                    "id кадров обязаны расти и после state при потере окна: клиент, запомнивший id кадра state, "
                            + "не должен получить меньший id следом: state " + stateId + ", затем " + id);
        }
    }

    @Test
    void nextFrame_AfterQuietKeepaliveTimeout_EmitsKeepaliveComment() throws Exception {
        TaskEventLog log = new TaskEventLog();

        try (TaskEventStream stream = fresh(RUNNING, log, 0, new AtomicInteger())) {
            String state = stream.nextFrame();
            assertTrue(state.contains("event: state"), "Свежий поток обязан начинаться с state: " + state);
            String keepalive = stream.nextFrame();
            assertEquals(
                    ": keepalive\n\n",
                    keepalive,
                    "Тишина обязана отбиваться кипалайв-комментарием, а не рвать соединение");
        }
    }

    @Test
    void close_MultipleTimes_RunsOnCloseOnce() {
        TaskEventLog log = new TaskEventLog();
        AtomicInteger closes = new AtomicInteger();

        TaskEventStream stream = fresh(RUNNING, log, 0, closes);
        stream.close();
        stream.close();

        assertEquals(1, closes.get(), "Освобождение слота обязано произойти ровно один раз");
    }

    @Test
    void nextFrame_AfterClose_StillDrainsQueuedFramesThenEnds() throws Exception {
        TaskEventLog log = new TaskEventLog();
        TaskEventStream stream = fresh(RUNNING, log, 0, new AtomicInteger());

        String queued = stream.nextFrame();
        stream.close();

        assertTrue(queued.contains("event: state"), "Кадр, взятый до закрытия, обязана доставляться: " + queued);
        assertNull(stream.nextFrame(), "После close поток обязан отдавать null без ожидания");
    }
}
