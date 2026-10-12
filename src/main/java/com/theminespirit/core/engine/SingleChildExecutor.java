package com.theminespirit.core.engine;

import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.policy.PolicyExemptions;
import java.util.Optional;

abstract class SingleChildExecutor extends NodeExecutor {
    private final ScriptRegistry scripts;
    private final ConditionRegistry conditions;
    private NodeExecutor current;

    SingleChildExecutor(ScriptRegistry scripts, ConditionRegistry conditions) {
        this.scripts = scripts;
        this.conditions = conditions;
    }

    final NodeExecutor current() {
        return current;
    }

    final void current(NodeExecutor child) {
        current = child;
    }

    final NodeExecutor spawn(TaskNode node) {
        return NodeExecutor.create(node, scripts, conditions);
    }

    @Override
    final String currentScript() {
        return current == null ? null : current.currentScript();
    }

    @Override
    final Optional<ScriptInstance.Progress> scriptProgress() {
        return current == null ? Optional.empty() : current.scriptProgress();
    }

    @Override
    final void cancel() {
        if (current != null) {
            current.cancel();
        }
    }

    @Override
    final void suspend() {
        if (current != null) {
            current.suspend();
        }
    }

    @Override
    final void resume() {
        if (current != null) {
            current.resume();
        }
    }

    @Override
    final PolicyExemptions exemptions() {
        return current == null ? PolicyExemptions.empty() : current.exemptions();
    }
}
