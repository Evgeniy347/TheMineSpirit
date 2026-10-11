package com.theminespirit.core.engine;

import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.TaskNode;

final class RepeatExecutor extends StepExecutor {
    private final int times;
    private final TaskNode step;

    RepeatExecutor(RepeatNode node, ScriptRegistry scripts, ConditionRegistry conditions) {
        super(scripts, conditions);
        this.times = node.times();
        this.step = node.step();
    }

    @Override
    TaskNode stepAt(int index) {
        return index < times ? step : null;
    }

    @Override
    TaskProgress buildProgress() {
        return new TaskProgress.RepeatAt(index(), current() == null ? null : current().snapshot());
    }

    @Override
    void applyRestore(TaskProgress progress) {
        TaskProgress.RepeatAt at = (TaskProgress.RepeatAt) progress;
        index(at.index());
        if (at.child() != null) {
            current(spawn(stepAt(at.index())));
            current().restore(at.child());
        }
    }
}
