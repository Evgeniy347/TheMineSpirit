package com.theminespirit.core.engine;

import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.TaskNode;
import org.jspecify.annotations.Nullable;

final class RepeatExecutor extends StepExecutor {
    private final int times;
    private final TaskNode step;

    RepeatExecutor(RepeatNode node, ScriptRegistry scripts, ConditionRegistry conditions) {
        super(scripts, conditions);
        this.times = node.times();
        this.step = node.step();
    }

    @Override
    @Nullable
    TaskNode stepAt(int index) {
        return index < times ? step : null;
    }

    @Override
    TaskProgress buildProgress() {
        return new TaskProgress.RepeatAt(index(), childProgress());
    }

    @Override
    void applyRestore(TaskProgress progress) {
        TaskProgress.RepeatAt at = (TaskProgress.RepeatAt) progress;
        index(at.index());
        TaskProgress child = at.child();
        if (child != null) {
            restoreChild(stepAt(at.index()), child);
        }
    }
}
