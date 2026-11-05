package com.theminespirit.core.engine;

import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.policy.PolicyExemptions;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

abstract class SingleChildExecutor extends NodeExecutor {
    private final ScriptRegistry scripts;
    private final ConditionRegistry conditions;
    private @Nullable NodeExecutor current;
    private Optional<ScriptResult> completedResult = Optional.empty();

    SingleChildExecutor(ScriptRegistry scripts, ConditionRegistry conditions) {
        this.scripts = scripts;
        this.conditions = conditions;
    }

    final @Nullable NodeExecutor current() {
        return current;
    }

    final NodeExecutor running() {
        NodeExecutor child = current;
        if (child == null) {
            throw new IllegalStateException("Узел " + getClass().getSimpleName() + " тикает без текущего шага: "
                    + "шаг создаётся до тика, а завершённый узел больше не тикается");
        }
        return child;
    }

    final @Nullable TaskProgress childProgress() {
        NodeExecutor child = current;
        return child == null ? null : child.snapshot();
    }

    final void current(@Nullable NodeExecutor child) {
        current = child;
    }

    final void restoreChild(@Nullable TaskNode node, TaskProgress progress) {
        if (node == null) {
            throw new SnapshotFormatException("Снимок хранит прогресс шага, которого нет в плане: "
                    + "план изменился после сохранения или снимок испорчен");
        }
        NodeExecutor child = spawn(node);
        current = child;
        child.restore(progress);
    }

    final NodeExecutor spawn(TaskNode node) {
        return NodeExecutor.create(node, scripts, conditions);
    }

    @Override
    final @Nullable String currentScript() {
        return current == null ? null : current.currentScript();
    }

    @Override
    final boolean observesWhileTacticsOwns() {
        return current != null && current.observesWhileTacticsOwns();
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

    final void rememberCompleted() {
        if (current != null) {
            completedResult = current.result();
        }
    }

    @Override
    Optional<ScriptResult> result() {
        return current != null ? current.result() : completedResult;
    }
}
