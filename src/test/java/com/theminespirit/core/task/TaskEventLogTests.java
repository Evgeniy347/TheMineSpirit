package com.theminespirit.core.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

class TaskEventLogTests {
    private static List<TaskEvent> events(int count, String type) {
        List<TaskEvent> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new TaskEvent(0, type, Map.of("i", i)));
        }
        return list;
    }

    @Test
    void append_TwoHundredEvents_ReaderInOtherThreadGetsAllInOrderWithoutDuplicates() throws InterruptedException {
        TaskEventLog log = new TaskEventLog();
        int total = 200;
        CountDownLatch readerReady = new CountDownLatch(1);
        CountDownLatch writerDone = new CountDownLatch(1);
        AtomicReference<List<Long>> readIds = new AtomicReference<>(new ArrayList<>());
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread reader = new Thread(
                () -> {
                    try {
                        readerReady.await();
                        long last = 0;
                        List<Long> ids = new ArrayList<>(total);
                        while (ids.size() < total) {
                            TaskEventLog.ReadResult result = log.awaitAfter(last, Duration.ofSeconds(5));
                            if (result.kind() == TaskEventLog.Kind.EVENT) {
                                ids.add(result.event().id());
                                last = result.event().id();
                            } else if (result.kind() == TaskEventLog.Kind.CLOSED) {
                                break;
                            }
                        }
                        readIds.set(ids);
                    } catch (Throwable t) {
                        failure.set(t);
                    }
                },
                "sse-reader-test");
        reader.start();

        Thread writer = new Thread(
                () -> {
                    try {
                        readerReady.countDown();
                        for (int i = 0; i < total; i++) {
                            log.append(events(1, TaskEvent.TYPE_PROGRESS));
                            if (i % 10 == 0) {
                                Thread.yield();
                            }
                        }
                        log.close();
                        writerDone.countDown();
                    } catch (Throwable t) {
                        failure.set(t);
                    }
                },
                "sse-writer-test");
        writer.start();

        assertTrue(writerDone.await(10, TimeUnit.SECONDS), "Писатель обязан завершить работу за 10 с");
        reader.join(TimeUnit.SECONDS.toMillis(10));
        assertNull(failure.get(), "Ни писатель, ни читатель не должны падать: " + failure.get());
        List<Long> ids = readIds.get();
        assertEquals(total, ids.size(), "Читатель обязан получить все 200 событий, получили " + ids.size());
        for (int i = 0; i < total; i++) {
            assertEquals(i + 1, ids.get(i), "События обязаны идти по порядку возрастющих id без повторов");
        }
    }

    @Test
    void awaitAfter_NothingAppended_TimesOutWithoutEvent() throws InterruptedException {
        TaskEventLog log = new TaskEventLog();

        TaskEventLog.ReadResult result = log.awaitAfter(0, Duration.ofMillis(20));

        assertEquals(TaskEventLog.Kind.TIMEOUT, result.kind(), "Пустой журнал обязан таймаутиться, а не закрываться");
    }

    @Test
    void awaitAfter_ClosedAndDrained_ReturnsClosed() throws InterruptedException {
        TaskEventLog log = new TaskEventLog();
        log.append(events(1, TaskEvent.TYPE_STATE));
        log.close();
        long last = log.since(0).events().get(0).id();

        TaskEventLog.ReadResult drained = log.awaitAfter(last, Duration.ofSeconds(1));

        assertEquals(TaskEventLog.Kind.CLOSED, drained.kind(), "После финального кадра поток обязан закрыться");
    }

    @Test
    void since_ReaderWithStaleLastEventId_GetsGapAndRemainingEvents() {
        TaskEventLog log = new TaskEventLog();
        log.append(events(300, TaskEvent.TYPE_PROGRESS));

        TaskEventLog.Catchup catchup = log.since(1);

        assertTrue(
                catchup.gap(), "Last-Event-ID старше окна обязан помечаться gap: клиент поймёт, что кадры пропущены");
        assertEquals(
                TaskEventLog.CAPACITY,
                catchup.events().size(),
                "После вытеснения читатель обязан получить только остаток окна, а не 300 событий");
    }

    @Test
    void since_RecentLastEventId_NoGapOnlyLaterEvents() {
        TaskEventLog log = new TaskEventLog();
        log.append(events(5, TaskEvent.TYPE_PROGRESS));
        long last = log.since(0).events().get(2).id();

        TaskEventLog.Catchup catchup = log.since(last);

        assertEquals(false, catchup.gap(), "Ни один кадр не потерян — gap не должен подниматься");
        assertEquals(2, catchup.events().size(), "Читатель обязан получить только кадры после Last-Event-ID");
    }

    @Property(tries = 200)
    void append_AnyChunkSize_IdsStayIncreasingAndAllEventsKept(@ForAll int chunkSize) {
        TaskEventLog log = new TaskEventLog();
        int size = Math.floorMod(chunkSize, 40) + 1;
        log.append(events(size, TaskEvent.TYPE_PROGRESS));
        log.append(events(size, TaskEvent.TYPE_STATE));

        List<TaskEvent> all = log.since(0).events();

        assertEquals(size * 2, all.size(), "Оба блока событий обязаны целиком остаться в журнале");
        long previous = 0;
        for (TaskEvent event : all) {
            assertTrue(event.id() > previous, "id событий обязаны строго возрастать: " + previous + " → " + event.id());
            previous = event.id();
        }
    }
}
