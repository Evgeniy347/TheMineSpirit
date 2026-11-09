package com.theminespirit.core.log;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class EventLog implements AutoCloseable {
    public static final int CAPACITY = 8192;
    public static final long ROTATE_BYTES = 64L * 1024 * 1024;
    private static final int BATCH = 256;
    private static final long POLL_MILLIS = 200;

    public interface Sink extends AutoCloseable {
        void write(LogRecord record) throws IOException;

        @Override
        void close() throws IOException;
    }

    private final BlockingQueue<LogRecord> queue;
    private final AtomicLong dropped = new AtomicLong();
    private final Sink sink;
    private final Thread worker;
    private volatile boolean running = true;

    public EventLog(Path directory, String build, String source) {
        this(new JsonlSink(directory, ROTATE_BYTES), build, source, CAPACITY);
    }

    EventLog(Sink sink, String build, String source, int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Ёмкость очереди " + capacity + ": нужна хотя бы одна запись");
        }
        this.sink = sink;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.worker = new Thread(() -> work(build, source), "theminespirit-log");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    public void append(LogRecord record) {
        if (!queue.offer(record)) {
            dropped.incrementAndGet();
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            worker.join(10_000);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        if (worker.isAlive()) {
            throw new IllegalStateException(
                    "Поток записи логов не остановился за 10 секунд: " + queue.size() + " записей ещё в очереди");
        }
    }

    private void work(String build, String source) {
        List<LogRecord> batch = new ArrayList<>(BATCH);
        while (running || !queue.isEmpty()) {
            try {
                LogRecord first = queue.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                }
                queue.drainTo(batch, BATCH - batch.size());
                reportDropped(build, source);
                if (!batch.isEmpty()) {
                    for (LogRecord record : batch) {
                        sink.write(record);
                    }
                    batch.clear();
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException error) {
                throw new IllegalStateException(
                        "Поток записи логов не записал файл событий: " + error.getMessage(), error);
            }
        }
        reportDropped(build, source);
        try {
            sink.close();
        } catch (IOException error) {
            throw new IllegalStateException("Файл событий не закрылся: " + error.getMessage(), error);
        }
    }

    private void reportDropped(String build, String source) {
        long lost = dropped.getAndSet(0);
        if (lost == 0) {
            return;
        }
        LogRecord record = LogRecord.builder(LogType.MESSAGE, System.currentTimeMillis(), 0, build, source)
                .put("level", "WARN")
                .put("tag", "logqueue")
                .put("class", EventLog.class.getName())
                .put(
                        "text",
                        "очередь логов переполнена: потеряно записей " + lost
                                + "; тик не останавливался, но часть событий не попала в файл")
                .build();
        try {
            sink.write(record);
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Событие о переполнении очереди не записалось: " + error.getMessage(), error);
        }
    }
}
