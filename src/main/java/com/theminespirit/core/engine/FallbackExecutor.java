package com.theminespirit.core.engine;

import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;

final class FallbackExecutor extends SingleChildExecutor {
    private static final String ALL_FAILED = "Все ветки fallback провалились: ни один шаг не завершился успехом";

    private final List<TaskNode> steps;
    private int index;
    private String lastReason;
    private Throwable lastCause;

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
        ScriptStatus child = current().tick();
        if (child == ScriptStatus.SUCCEEDED) {
            rememberCompleted();
            return ScriptStatus.SUCCEEDED;
        }
        if (child == ScriptStatus.CANCELLED) {
            return ScriptStatus.CANCELLED;
        }
        if (child == ScriptStatus.FAILED) {
            lastReason = current().failureReason();
            lastCause = current().failureCause().orElse(null);
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
    }

    @Override
    TaskProgress buildProgress() {
        return new TaskProgress.FallbackAt(index, lastReason, current() == null ? null : current().snapshot());
    }

    @Override
    void applyRestore(TaskProgress progress) {
        TaskProgress.FallbackAt at = (TaskProgress.FallbackAt) progress;
        index = at.index();
        lastReason = at.lastReason();
        if (at.child() != null) {
            current(spawn(steps.get(at.index())));
            current().restore(at.child());
        }
    }
}
