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
            failFrom(current());
            return ScriptStatus.FAILED;
        }
        if (child == ScriptStatus.CANCELLED) {
            return ScriptStatus.CANCELLED;
        }
        if (child == ScriptStatus.SUCCEEDED) {
            rememberCompleted();
            index++;
            current(null);
            if (stepAt(index) == null) {
                return ScriptStatus.SUCCEEDED;
            }
        }
        return ScriptStatus.RUNNING;
    }

    abstract TaskNode stepAt(int index);

    final int index() {
        return index;
    }

    final void index(int value) {
        index = value;
    }
}
