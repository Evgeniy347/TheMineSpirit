package com.theminespirit.core.task;

import com.theminespirit.core.engine.RunProgress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TaskRegistry {
    public static final int MAX_COMPLETED = 256;

    private final Map<String, Entry> tasks = new LinkedHashMap<>();
    private long nextId;

    public synchronized String reserve(String bot, String goal) {
        Objects.requireNonNull(bot, "bot не может быть null: задача всегда привязана к духу");
        TaskRecord active = activeForBot(bot).orElse(null);
        if (active != null) {
            throw new BusyException(bot, active.taskId());
        }
        nextId++;
        String taskId = "t-" + nextId;
        tasks.put(taskId, new Entry(new TaskRecord(taskId, bot, TaskState.ACCEPTED, goal, null, null, null)));
        ensureCompletedCap();
        return taskId;
    }

    public synchronized Optional<TaskRecord> get(String taskId) {
        Entry entry = tasks.get(taskId);
        return entry == null ? Optional.empty() : Optional.of(entry.record);
    }

    public synchronized boolean discard(String taskId) {
        Entry entry = tasks.get(taskId);
        if (entry == null || entry.started || entry.record.state() != TaskState.ACCEPTED) {
            return false;
        }
        tasks.remove(taskId);
        entry.log.close();
        return true;
    }

    public synchronized void markStarted(String taskId) {
        markStarted(taskId, SpiritReading.UNKNOWN_GENERATION);
    }

    public synchronized void markStarted(String taskId, long generation) {
        Entry entry = tasks.get(taskId);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "Задача '" + taskId + "' не найдена в реестре: помечать запущенной нечего");
        }
        entry.started = true;
        entry.generation = generation;
    }

    public synchronized long startedGeneration(String taskId) {
        Entry entry = tasks.get(taskId);
        return entry == null ? SpiritReading.UNKNOWN_GENERATION : entry.generation;
    }

    public synchronized List<TaskRecord> watchableRecords() {
        List<TaskRecord> watchable = new ArrayList<>();
        for (Entry entry : tasks.values()) {
            if (entry.started && entry.record.active()) {
                watchable.add(entry.record);
            }
        }
        return List.copyOf(watchable);
    }

    public synchronized Optional<TaskRecord> activeForBot(String botId) {
        for (Entry entry : tasks.values()) {
            if (entry.record.active() && entry.record.bot().equals(botId)) {
                return Optional.of(entry.record);
            }
        }
        return Optional.empty();
    }

    public synchronized TaskEventLog log(String taskId) {
        Entry entry = tasks.get(taskId);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "Задача '" + taskId + "' не найдена в реестре: журнал событий есть " + "только у известной задачи");
        }
        return entry.log;
    }

    public synchronized List<TaskEvent> observe(String taskId, TaskState state, RunProgress progress, String error) {
        return observe(taskId, state, progress, null, error);
    }

    public synchronized List<TaskEvent> observe(
            String taskId, TaskState state, RunProgress progress, Object result, String error) {
        Entry entry = tasks.get(taskId);
        if (entry == null) {
            throw new IllegalArgumentException("Задача '" + taskId + "' не найдена в реестре: обновлять нечего");
        }
        TaskRecord previous = entry.record;
        if (!previous.state().canTransitionTo(state)) {
            throw new IllegalStateException("Недопустимый переход задачи " + taskId + ": "
                    + previous.state().wire()
                    + " → " + state.wire() + ". Разрешены accepted → running/succeeded/failed/cancelled и "
                    + "running → succeeded/failed/cancelled");
        }
        Object nextResult = result != null ? result : previous.result();
        TaskRecord next = new TaskRecord(taskId, previous.bot(), state, previous.goal(), progress, nextResult, error);
        entry.record = next;
        List<TaskEvent> events = TaskEventDiff.between(previous, next);
        entry.log.append(events);
        if (state.isTerminal()) {
            entry.log.close();
            ensureCompletedCap();
        }
        return events;
    }

    public synchronized boolean observeIfActive(String taskId, TaskState state, RunProgress progress, String error) {
        return observeIfActive(taskId, state, progress, null, error);
    }

    public synchronized boolean observeIfActive(
            String taskId, TaskState state, RunProgress progress, Object result, String error) {
        Entry entry = tasks.get(taskId);
        if (entry == null || !entry.record.active()) {
            return false;
        }
        observe(taskId, state, progress, result, error);
        return true;
    }

    public synchronized Optional<TaskRecord> cancelIfActive(String taskId) {
        Entry entry = tasks.get(taskId);
        if (entry == null || !entry.record.active()) {
            return Optional.empty();
        }
        observe(taskId, TaskState.CANCELLED, entry.record.progress(), null);
        return Optional.of(entry.record);
    }

    public synchronized Optional<Snapshot> snapshot(String taskId, long lastEventId) {
        Entry entry = tasks.get(taskId);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(
                new Snapshot(entry.record, entry.log, entry.log.since(lastEventId), entry.log.lastId(), lastEventId));
    }

    public record Snapshot(
            TaskRecord record, TaskEventLog log, TaskEventLog.Catchup catchup, long position, long lastEventId) {}

    public synchronized void closeAll() {
        for (Entry entry : tasks.values()) {
            entry.log.close();
        }
    }

    public synchronized int size() {
        return tasks.size();
    }

    public synchronized int completedCount() {
        int completed = 0;
        for (Entry entry : tasks.values()) {
            if (entry.record.state().isTerminal()) {
                completed++;
            }
        }
        return completed;
    }

    private void ensureCompletedCap() {
        int completed = completedCount();
        if (completed <= MAX_COMPLETED) {
            return;
        }
        List<String> finished = new ArrayList<>();
        for (Map.Entry<String, Entry> entry : tasks.entrySet()) {
            if (entry.getValue().record.state().isTerminal()) {
                finished.add(entry.getKey());
            }
        }
        int excess = completed - MAX_COMPLETED;
        for (int i = 0; i < excess; i++) {
            Entry removed = tasks.remove(finished.get(i));
            removed.log.close();
        }
    }

    public static final class BusyException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final transient String bot;
        private final transient String activeTaskId;

        public BusyException(String bot, String activeTaskId) {
            super("Дух '" + bot + "' уже выполняет задачу " + activeTaskId + ": очередь и вытеснение выключены, "
                    + "дождитесь завершения или отмените задачу DELETE /v1/tasks/" + activeTaskId);
            this.bot = bot;
            this.activeTaskId = activeTaskId;
        }

        public String bot() {
            return bot;
        }

        public String activeTaskId() {
            return activeTaskId;
        }
    }

    private static final class Entry {
        private TaskRecord record;
        private boolean started;
        private long generation = SpiritReading.UNKNOWN_GENERATION;
        private final TaskEventLog log = new TaskEventLog();

        Entry(TaskRecord record) {
            this.record = record;
        }
    }
}
