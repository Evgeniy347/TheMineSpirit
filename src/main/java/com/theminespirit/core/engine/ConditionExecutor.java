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
    private boolean resolved;

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
        if (!resolved) {
            resolved = true;
            Optional<ConditionEvaluator> evaluator = conditions.find(check);
            if (evaluator.isEmpty()) {
                fail("Неизвестная проверка '" + check + "': она не зарегистрирована в реестре условий");
                return ScriptStatus.FAILED;
            }
            boolean passed;
            try {
                passed = evaluator.get().test(params);
            } catch (RuntimeException error) {
                fail("проверка '" + check + "' упала: " + error.getMessage());
                return ScriptStatus.FAILED;
            }
            TaskNode branch = passed ? thenStep : elseStep;
            if (branch == null) {
                return ScriptStatus.SUCCEEDED;
            }
            current(spawn(branch));
        }
        ScriptStatus child = current().tick();
        if (child == ScriptStatus.FAILED) {
            fail(current().failureReason());
            return ScriptStatus.FAILED;
        }
        return child;
    }
}
