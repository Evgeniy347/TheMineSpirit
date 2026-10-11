package com.theminespirit.core.engine;

import com.theminespirit.core.plan.TaskNode;
import java.util.Objects;
import java.util.Optional;

public final class TaskRunner {
    private final NodeExecutor root;
    private RunStatus status = RunStatus.RUNNING;
    private String reason;

    private TaskRunner(NodeExecutor root) {
        this.root = root;
    }

    public static TaskRunner start(TaskNode plan, ScriptRegistry scripts, ConditionRegistry conditions) {
        Objects.requireNonNull(plan, "plan не может быть null: без дерева задач исполнять нечего");
        Objects.requireNonNull(scripts, "scripts не может быть null: без реестра движок не создаст скрипты");
        Objects.requireNonNull(conditions, "conditions не может быть null: без реестра условие не выберет ветку");
        return new TaskRunner(NodeExecutor.create(plan, scripts, conditions));
    }

    public RunStatus tick() {
        if (status != RunStatus.RUNNING) {
            return status;
        }
        ScriptStatus node = root.tick();
        if (node == ScriptStatus.SUCCEEDED) {
            status = RunStatus.SUCCEEDED;
        } else if (node == ScriptStatus.FAILED) {
            status = RunStatus.FAILED;
            reason = root.failureReason();
        }
        return status;
    }

    public RunStatus status() {
        return status;
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(reason);
    }

    public Optional<String> currentScript() {
        if (status != RunStatus.RUNNING) {
            return Optional.empty();
        }
        return Optional.ofNullable(root.currentScript());
    }

    public void cancel() {
        if (status != RunStatus.RUNNING) {
            return;
        }
        status = RunStatus.CANCELLED;
        root.cancel();
    }
}
