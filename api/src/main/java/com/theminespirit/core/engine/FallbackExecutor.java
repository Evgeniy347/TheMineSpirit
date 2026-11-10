package com.theminespirit.core.engine;

import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class FallbackExecutor extends SingleChildExecutor {
    private static final String ALL_FAILED = "Все ветки fallback провалились: ни один шаг не завершился успехом";

    private final List<TaskNode> steps;
    private int index;
    private @Nullable String lastReason;
    private @Nullable Throwable lastCause;
    private Optional<FailureFact> lastFact = Optional.empty();

    FallbackExecutor(FallbackNode node, ScriptRegistry scripts, ConditionRegistry conditions) {
        super(scripts, conditions);
        this.steps = node.steps();
    }

    @Override
    ScriptStatus advance() {
        if (index >= steps.size()) {
            failAll();
            return ScriptStatus.FAILED;
        }
        if (current() == null) {
            current(spawn(steps.get(index)));
        }
        ScriptStatus child = running().tick();
        if (child == ScriptStatus.SUCCEEDED) {
            rememberCompleted();
            return ScriptStatus.SUCCEEDED;
        }
        if (child == ScriptStatus.CANCELLED) {
            return ScriptStatus.CANCELLED;
        }
        if (child == ScriptStatus.FAILED) {
            lastReason = running().failureReason();
            lastCause = running().failureCause().orElse(null);
            lastFact = running().failureFact();
            index++;
            current(null);
            if (index >= steps.size()) {
                failAll();
                return ScriptStatus.FAILED;
            }
        }
        return ScriptStatus.RUNNING;
    }

    private void failAll() {
        fail(lastReason != null ? lastReason : ALL_FAILED, lastCause);
        failureFact(lastFact.orElse(null));
    }

    @Override
    TaskProgress buildProgress() {
        return new TaskProgress.FallbackAt(index, lastReason, childProgress());
    }

    @Override
    void applyRestore(TaskProgress progress) {
        TaskProgress.FallbackAt at = (TaskProgress.FallbackAt) progress;
        index = at.index();
        lastReason = at.lastReason();
        TaskProgress child = at.child();
        if (child != null) {
            restoreChild(at.index() < steps.size() ? steps.get(at.index()) : null, child);
        }
    }
}
