package com.theminespirit.core.task;

import com.theminespirit.core.engine.RunProgress;
import com.theminespirit.core.engine.RunStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class TaskReconciler {
    public static final int ABSENT_GRACE_TICKS = 100;
    static final String SPIRIT_GONE = "дух исчез: сущность не вернулась в мир за %d тиков, задача потеряна";
    static final String SPIRIT_DEAD = "дух исчез: он погиб, задача прервана";
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
                finish(record, TaskState.FAILED, SPIRIT_GONE.formatted(ABSENT_GRACE_TICKS));
            }
            return;
        }
        absentTicks.remove(taskId);
        if (!reading.alive()) {
            finish(record, TaskState.FAILED, SPIRIT_DEAD);
            return;
        }
        RunStatus status = reading.status();
        if (status == null) {
            return;
        }
        switch (status) {
            case RUNNING -> running(record, reading.progress());
            case SUCCEEDED -> finish(record, TaskState.SUCCEEDED, null);
            case CANCELLED -> finish(record, TaskState.CANCELLED, null);
            case FAILED -> finish(record.withProgress(reading.failedAt()), TaskState.FAILED,
                    reading.failureReason() == null ? NO_REASON : reading.failureReason());
        }
    }

    private void running(TaskRecord record, RunProgress progress) {
        RunProgress next = progress == null ? record.progress() : progress;
        if (record.state() == TaskState.RUNNING && Objects.equals(next, record.progress())) {
            return;
        }
        registry.observeIfActive(record.taskId(), TaskState.RUNNING, next, null);
    }

    private void finish(TaskRecord record, TaskState state, String error) {
        absentTicks.remove(record.taskId());
        registry.observeIfActive(record.taskId(), state, record.progress(), error);
    }
}
