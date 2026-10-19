package com.theminespirit.core.task;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class TaskEventLog {
    public static final int CAPACITY = 256;

    public enum Kind {
        EVENT,
        TIMEOUT,
        CLOSED
    }

    public record ReadResult(Kind kind, TaskEvent event) {
        public static ReadResult event(TaskEvent event) {
            return new ReadResult(Kind.EVENT, event);
        }

        public static ReadResult timeout() {
            return new ReadResult(Kind.TIMEOUT, null);
        }

        public static ReadResult closed() {
            return new ReadResult(Kind.CLOSED, null);
        }
    }

    public record Catchup(boolean gap, List<TaskEvent> events) {
        public Catchup {
            events = List.copyOf(events);
        }
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final Deque<TaskEvent> events = new ArrayDeque<>();
    private long nextId = 1;
    private boolean closed;

    public void append(List<TaskEvent> incoming) {
        if (incoming.isEmpty()) {
            return;
        }
        lock.lock();
        try {
            if (closed) {
                return;
            }
            for (TaskEvent event : incoming) {
                events.addLast(event.withId(nextId++));
            }
            while (events.size() > CAPACITY) {
                events.removeFirst();
            }
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        try {
            closed = true;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public Catchup since(long lastId) {
        lock.lock();
        try {
            List<TaskEvent> pending = new ArrayList<>();
            long oldest = events.isEmpty() ? nextId : events.peekFirst().id();
            boolean gap = lastId > 0 && oldest > lastId + 1;
            for (TaskEvent event : events) {
                if (event.id() > lastId) {
                    pending.add(event);
                }
            }
            return new Catchup(gap, pending);
        } finally {
            lock.unlock();
        }
    }

    public ReadResult awaitAfter(long lastId, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        lock.lock();
        try {
            while (true) {
                for (TaskEvent event : events) {
                    if (event.id() > lastId) {
                        return ReadResult.event(event);
                    }
                }
                if (closed) {
                    return ReadResult.closed();
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return ReadResult.timeout();
                }
                changed.awaitNanos(remaining);
            }
        } finally {
            lock.unlock();
        }
    }

    public long lastId() {
        lock.lock();
        try {
            return nextId - 1;
        } finally {
            lock.unlock();
        }
    }
}
