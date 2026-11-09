package com.theminespirit.core.task;

import com.theminespirit.core.engine.RunProgress;
import com.theminespirit.core.engine.RunStatus;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class TaskReconciler {
    public static final int ABSENT_GRACE_TICKS = 100;
    static final String SPIRIT_GONE = "дух исчез: сущность не вернулась в мир за %d тиков, задача потеряна";
    static final String SPIRIT_DEAD = "дух исчез: он погиб, задача прервана";
    static final String DISPLACED = "задача вытеснена вне REST: дух получил другую задачу от доски (граф), командой "
            + "/spirit или планировщиком ИИ, эта задача остановлена";
    static final String NO_REASON = "скрипт провалился без указания причины";

    private final TaskRegistry registry;
    private final Map<String, Integer> absentTicks = new HashMap<>();

    public TaskReconciler(TaskRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry не может быть null: обновлять нечего");
    }

    public void reconcile(TaskRecord record, SpiritReading reading) {
        String taskId = record.taskId();
        if (!reading.present()) {
            int absent = absentTicks.merge(taskId, 1, Integer::sum);
            if (absent >= ABSENT_GRACE_TICKS) {
                finish(record, TaskState.FAILED, SPIRIT_GONE.formatted(ABSENT_GRACE_TICKS), null);
            }
            return;
        }
        absentTicks.remove(taskId);
        if (!reading.alive()) {
            finish(record, TaskState.FAILED, SPIRIT_DEAD, null);
            return;
        }
        if (displaced(record, reading)) {
            finish(record, TaskState.CANCELLED, DISPLACED, null);
            return;
        }
        RunStatus status = reading.status();
        if (status == null) {
            return;
        }
        switch (status) {
            case RUNNING -> running(record, reading.progress());
            case SUCCEEDED -> finish(record, TaskState.SUCCEEDED, null, reading.result());
            case CANCELLED -> finish(record, TaskState.CANCELLED, null, null);
            case FAILED ->
                finish(
                        record.withProgress(reading.failedAt()),
                        TaskState.FAILED,
                        reading.failureReason() == null ? NO_REASON : reading.failureReason(),
                        null);
        }
    }

    public void forgetUnobserved(List<TaskRecord> observed) {
        Set<String> observedIds = new HashSet<>();
        for (TaskRecord record : observed) {
            observedIds.add(record.taskId());
        }
        absentTicks.keySet().retainAll(observedIds);
    }

    private boolean displaced(TaskRecord record, SpiritReading reading) {
        long expected = registry.startedGeneration(record.taskId());
        return expected != SpiritReading.UNKNOWN_GENERATION
                && reading.generation() != SpiritReading.UNKNOWN_GENERATION
                && reading.generation() != expected;
    }

    private void running(TaskRecord record, @Nullable RunProgress progress) {
        boolean namesScript = progress != null && progress.currentScript() != null;
        RunProgress next = namesScript ? progress : record.progress();
        if (record.state() == TaskState.RUNNING && Objects.equals(next, record.progress())) {
            return;
        }
        registry.observeIfActive(record.taskId(), TaskState.RUNNING, next, null);
    }

    private void finish(TaskRecord record, TaskState state, @Nullable String error, @Nullable Object result) {
        absentTicks.remove(record.taskId());
        registry.observeIfActive(record.taskId(), state, record.progress(), result, error);
    }
}
