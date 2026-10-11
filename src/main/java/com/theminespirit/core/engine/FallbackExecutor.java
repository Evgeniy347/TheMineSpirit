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
            return ScriptStatus.SUCCEEDED;
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
}
