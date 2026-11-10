package com.theminespirit.core.engine;

import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.policy.PolicyExemptions;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class TaskRunner {
    private final @Nullable TaskNode plan;
    private final @Nullable NodeExecutor root;
    private RunStatus status = RunStatus.RUNNING;
    private boolean suspended;
    private @Nullable String reason;
    private @Nullable Throwable cause;
    private @Nullable RunProgress failedAt;
    private Optional<FailureFact> fact = Optional.empty();
    private Optional<ScriptResult> successResult = Optional.empty();

    private TaskRunner(@Nullable TaskNode plan, @Nullable NodeExecutor root) {
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
        TaskNode plan = snapshot.plan();
        TaskProgress progress = snapshot.progress();
        if (snapshot.status() == RunStatus.RUNNING) {
            if (plan == null || progress == null) {
                throw new SnapshotFormatException("RUNNING-снимок без плана или прогресса: продолжать нечего");
            }
            NodeExecutor restored = NodeExecutor.create(plan, scripts, conditions);
            restored.restore(progress);
            return new TaskRunner(plan, restored);
        }
        return runner;
    }

    public Optional<TaskSnapshot> snapshot() {
        if (status != RunStatus.RUNNING) {
            return Optional.empty();
        }
        return Optional.of(new TaskSnapshot(
                TaskSnapshot.VERSION, RunStatus.RUNNING, reason, plan, running().snapshot()));
    }

    public RunStatus tick() {
        if (status != RunStatus.RUNNING || suspended) {
            return status;
        }
        NodeExecutor running = running();
        ScriptStatus node = running.tick();
        if (node == ScriptStatus.SUCCEEDED) {
            status = RunStatus.SUCCEEDED;
            successResult = running.result();
        } else if (node == ScriptStatus.FAILED) {
            status = RunStatus.FAILED;
            reason = running.failureReason();
            cause = running.failureCause().orElse(null);
            fact = running.failureFact();
            failedAt = running.progress();
        } else if (node == ScriptStatus.CANCELLED) {
            status = RunStatus.CANCELLED;
            suspended = false;
        }
        return status;
    }

    public RunStatus status() {
        return status;
    }

    public Optional<TaskNode> plan() {
        return Optional.ofNullable(plan);
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(reason);
    }

    public Optional<Throwable> failureCause() {
        return Optional.ofNullable(cause);
    }

    public Optional<FailureFact> failureFact() {
        return fact;
    }

    public Optional<ScriptResult> successResult() {
        return successResult;
    }

    public Optional<String> currentScript() {
        if (status != RunStatus.RUNNING) {
            return Optional.empty();
        }
        return Optional.ofNullable(running().currentScript());
    }

    public boolean observesWhileTacticsOwns() {
        return status == RunStatus.RUNNING && running().observesWhileTacticsOwns();
    }

    public void suspend() {
        if (status == RunStatus.RUNNING && !suspended) {
            suspended = true;
            running().suspend();
        }
    }

    public void resume() {
        if (status == RunStatus.RUNNING && suspended) {
            suspended = false;
            running().resume();
        }
    }

    public boolean isSuspended() {
        return suspended;
    }

    public PolicyExemptions policyExemptions() {
        return status == RunStatus.RUNNING ? running().exemptions() : PolicyExemptions.empty();
    }

    public Optional<RunProgress> progress() {
        if (status != RunStatus.RUNNING) {
            return Optional.empty();
        }
        return Optional.of(running().progress());
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
        running().cancel();
    }

    private NodeExecutor running() {
        NodeExecutor executor = root;
        if (executor == null) {
            throw new IllegalStateException("Задача в статусе " + status + " без исполнителя: исполнитель есть только "
                    + "у RUNNING-задачи, завершённая восстановленная задача ничего не исполняет");
        }
        return executor;
    }
}
