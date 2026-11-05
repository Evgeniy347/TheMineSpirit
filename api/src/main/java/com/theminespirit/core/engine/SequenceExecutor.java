package com.theminespirit.core.engine;

import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class SequenceExecutor extends StepExecutor {
    private final List<TaskNode> steps;

    SequenceExecutor(SequenceNode node, ScriptRegistry scripts, ConditionRegistry conditions) {
        super(scripts, conditions);
        this.steps = node.steps();
    }

    @Override
    @Nullable
    TaskNode stepAt(int index) {
        return index < steps.size() ? steps.get(index) : null;
    }

    @Override
    int stepNumber() {
        int total = totalSteps();
        return Math.min(index() + 1, total);
    }

    @Override
    int totalSteps() {
        return Math.max(steps.size(), 1);
    }

    @Override
    TaskProgress buildProgress() {
        return new TaskProgress.SequenceAt(index(), childProgress());
    }

    @Override
    void applyRestore(TaskProgress progress) {
        TaskProgress.SequenceAt at = (TaskProgress.SequenceAt) progress;
        index(at.index());
        TaskProgress child = at.child();
        if (child != null) {
            restoreChild(stepAt(at.index()), child);
        }
    }
}
