package com.theminespirit.core.engine;

import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.Map;
import java.util.Optional;

final class ConditionExecutor extends SingleChildExecutor {
    private final String check;
    private final Map<String, Object> params;
    private final TaskNode thenStep;
    private final TaskNode elseStep;
    private final ConditionRegistry conditions;
    private TaskProgress.ConditionBranch branch = TaskProgress.ConditionBranch.UNRESOLVED;

    ConditionExecutor(ConditionNode node, ScriptRegistry scripts, ConditionRegistry conditions) {
        super(scripts, conditions);
        this.check = node.check();
        this.params = node.params();
        this.thenStep = node.thenStep();
        this.elseStep = node.elseStep();
        this.conditions = conditions;
    }

    @Override
    ScriptStatus advance() {
        if (branch == TaskProgress.ConditionBranch.UNRESOLVED) {
            Optional<ConditionEvaluator> evaluator = conditions.find(check);
            if (evaluator.isEmpty()) {
                fail("Неизвестная проверка '" + check + "': она не зарегистрирована в реестре условий");
                return ScriptStatus.FAILED;
            }
            boolean passed;
            try {
                passed = evaluator.get().test(params);
            } catch (RuntimeException error) {
                fail("проверка '" + check + "' упала: " + NodeExecutor.describe(error), error);
                return ScriptStatus.FAILED;
            }
            branch = passed ? TaskProgress.ConditionBranch.THEN : TaskProgress.ConditionBranch.ELSE;
            TaskNode chosen = passed ? thenStep : elseStep;
            if (chosen == null) {
                return ScriptStatus.SUCCEEDED;
            }
            current(spawn(chosen));
        }
        ScriptStatus child = current().tick();
        if (child == ScriptStatus.FAILED) {
            failFrom(current());
            return ScriptStatus.FAILED;
        }
        if (child == ScriptStatus.SUCCEEDED) {
            rememberCompleted();
        }
        return child;
    }

    @Override
    TaskProgress buildProgress() {
        return new TaskProgress.ConditionAt(branch, current() == null ? null : current().snapshot());
    }

    @Override
    void applyRestore(TaskProgress progress) {
        TaskProgress.ConditionAt at = (TaskProgress.ConditionAt) progress;
        branch = at.branch();
        if (at.child() != null) {
            TaskNode chosen = branch == TaskProgress.ConditionBranch.THEN ? thenStep : elseStep;
            current(spawn(chosen));
            current().restore(at.child());
        }
    }
}
