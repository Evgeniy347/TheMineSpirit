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
}
