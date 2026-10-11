package com.theminespirit.core.engine;

import com.theminespirit.core.plan.TaskNode;

abstract class StepExecutor extends SingleChildExecutor {
    private int index;

    StepExecutor(ScriptRegistry scripts, ConditionRegistry conditions) {
        super(scripts, conditions);
    }

    @Override
    final ScriptStatus advance() {
        if (current() == null) {
            TaskNode next = stepAt(index);
            if (next == null) {
                return ScriptStatus.SUCCEEDED;
            }
            current(spawn(next));
        }
        ScriptStatus child = current().tick();
        if (child == ScriptStatus.FAILED) {
            fail(current().failureReason());
            return ScriptStatus.FAILED;
        }
        if (child == ScriptStatus.SUCCEEDED) {
            index++;
            current(null);
            if (stepAt(index) == null) {
                return ScriptStatus.SUCCEEDED;
            }
        }
        return ScriptStatus.RUNNING;
    }

    abstract TaskNode stepAt(int index);
}
