package com.theminespirit.core.task;

import com.theminespirit.core.engine.RunProgress;
import java.util.Objects;

public record TaskRecord(
        String taskId, String bot, TaskState state, String goal, RunProgress progress, Object result, String error) {
    public TaskRecord {
        Objects.requireNonNull(taskId, "taskId не может быть null: клиент находит задачу по task_id");
        Objects.requireNonNull(bot, "bot не может быть null: у задачи всегда есть дух-исполнитель");
        Objects.requireNonNull(state, "state не может быть null: состояние — ось жизненного цикла задачи");
    }

    public TaskRecord withProgress(RunProgress replacement) {
        return replacement == null ? this : new TaskRecord(taskId, bot, state, goal, replacement, result, error);
    }

    public boolean active() {
        return !state.isTerminal();
    }
}
