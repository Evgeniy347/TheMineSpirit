package com.theminespirit.core.engine;

import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;

final class SequenceExecutor extends StepExecutor {
    private final List<TaskNode> steps;

    SequenceExecutor(SequenceNode node, ScriptRegistry scripts, ConditionRegistry conditions) {
        super(scripts, conditions);
        this.steps = node.steps();
    }

    @Override
    TaskNode stepAt(int index) {
        return index < steps.size() ? steps.get(index) : null;
    }
}
