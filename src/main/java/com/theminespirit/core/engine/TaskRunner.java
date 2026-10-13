package com.theminespirit.core.engine;

import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.policy.PolicyExemptions;
import java.util.Objects;
import java.util.Optional;

public final class TaskRunner {
    private final TaskNode plan;
    private final NodeExecutor root;
    private RunStatus status = RunStatus.RUNNING;
    private boolean suspended;
    private String reason;
    private Throwable cause;
    private RunProgress failedAt;
    private Optional<ScriptResult> successResult = Optional.empty();

    private TaskRunner(TaskNode plan, NodeExecutor root) {
        this.plan = plan;
        this.root = root;
    }

    public static TaskRunner start(TaskNode plan, ScriptRegistry scripts, ConditionRegistry conditions) {
        Objects.requireNonNull(plan, "plan не может быть null: без дерева задач исполнять нечего");
        Objects.requireNonNull(scripts, "scripts не может быть null: без реестра движок не создаст скрипты");
        Objects.requireNonNull(conditions, "conditions не может быть null: без реестра условие не выберет ветку");
        return new TaskRunner(plan, NodeExecutor.create(plan, scripts, conditions));
    }

    public static TaskRunner restore(TaskSnapshot snapshot, ScriptRegistry scripts, ConditionRegistry conditions) {
        Objects.requireNonNull(snapshot, "snapshot не может быть null: восстанавливать нечего");
        Objects.requireNonNull(scripts, "scripts не может быть null: без реестра движок не создаст скрипты");
        Objects.requireNonNull(conditions, "conditions не может быть null: без реестра условие не выберет ветку");
        TaskRunner runner = new TaskRunner(snapshot.plan(), null);
        runner.status = snapshot.status();
        runner.reason = snapshot.reason();
        if (snapshot.status() == RunStatus.RUNNING) {
            NodeExecutor restored = NodeExecutor.create(snapshot.plan(), scripts, conditions);
            restored.restore(snapshot.progress());
            return new TaskRunner(snapshot.plan(), restored);
        }
        return runner;
    }

    public Optional<TaskSnapshot> snapshot() {
        if (status != RunStatus.RUNNING) {
            return Optional.empty();
        }
        return Optional.of(new TaskSnapshot(TaskSnapshot.VERSION, RunStatus.RUNNING, reason, plan, root.snapshot()));
    }

    public RunStatus tick() {
        if (status != RunStatus.RUNNING || suspended) {
            return status;
        }
        ScriptStatus node = root.tick();
        if (node == ScriptStatus.SUCCEEDED) {
            status = RunStatus.SUCCEEDED;
            successResult = root.result();
        } else if (node == ScriptStatus.FAILED) {
            status = RunStatus.FAILED;
            reason = root.failureReason();
            cause = root.failureCause().orElse(null);
            failedAt = root.progress();
        }
        return status;
    }

    public RunStatus status() {
        return status;
    }

    public TaskNode plan() {
        return plan;
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(reason);
    }

    public Optional<Throwable> failureCause() {
        return Optional.ofNullable(cause);
    }

    public Optional<ScriptResult> successResult() {
        return successResult;
    }

    public Optional<String> currentScript() {
        if (status != RunStatus.RUNNING) {
            return Optional.empty();
        }
        return Optional.ofNullable(root.currentScript());
    }

    public void suspend() {
        if (status == RunStatus.RUNNING && !suspended) {
            suspended = true;
            root.suspend();
        }
    }

    public void resume() {
        if (status == RunStatus.RUNNING && suspended) {
            suspended = false;
            root.resume();
        }
    }

    public boolean isSuspended() {
        return suspended;
    }

    public PolicyExemptions policyExemptions() {
        return status == RunStatus.RUNNING ? root.exemptions() : PolicyExemptions.empty();
    }

    public Optional<RunProgress> progress() {
        if (status != RunStatus.RUNNING) {
            return Optional.empty();
        }
        return Optional.of(root.progress());
    }

    public Optional<RunProgress> failedAt() {
        return Optional.ofNullable(failedAt);
    }

    public void cancel() {
        if (status != RunStatus.RUNNING) {
            return;
        }
        status = RunStatus.CANCELLED;
        suspended = false;
        root.cancel();
    }
}
