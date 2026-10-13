package com.theminespirit.core.engine;

import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.policy.PolicyExemptions;
import java.util.Optional;

abstract class NodeExecutor {
    private ScriptStatus status = ScriptStatus.RUNNING;
    private String reason;
    private Throwable cause;

    static NodeExecutor create(TaskNode node, ScriptRegistry scripts, ConditionRegistry conditions) {
        EngineCounters.executorCreations++;
        return switch (node) {
            case ScriptCall call -> new ScriptExecutor(call, scripts);
            case SequenceNode sequence -> new SequenceExecutor(sequence, scripts, conditions);
            case RepeatNode repeat -> new RepeatExecutor(repeat, scripts, conditions);
            case FallbackNode fallback -> new FallbackExecutor(fallback, scripts, conditions);
            case ParallelNode parallel -> new ParallelExecutor(parallel, scripts, conditions);
            case ConditionNode condition -> new ConditionExecutor(condition, scripts, conditions);
        };
    }

    final TaskProgress snapshot() {
        EngineCounters.snapshotVisits++;
        return buildProgress();
    }

    abstract TaskProgress buildProgress();

    final void restore(TaskProgress progress) {
        applyRestore(progress);
    }

    abstract void applyRestore(TaskProgress progress);

    final void forceTerminal(ScriptStatus terminal, String reason) {
        status = terminal;
        this.reason = reason;
        this.cause = null;
    }

    final ScriptStatus tick() {
        if (status != ScriptStatus.RUNNING) {
            return status;
        }
        ScriptStatus next = advance();
        if (next != ScriptStatus.RUNNING) {
            status = next;
        }
        return status;
    }

    abstract ScriptStatus advance();

    final String failureReason() {
        return reason;
    }

    final Optional<Throwable> failureCause() {
        return Optional.ofNullable(cause);
    }

    final void fail(String message) {
        fail(message, null);
    }

    final void fail(String message, Throwable cause) {
        status = ScriptStatus.FAILED;
        reason = message;
        this.cause = cause;
    }

    final void failFrom(NodeExecutor child) {
        fail(child.reason, child.cause);
    }

    static String describe(Throwable error) {
        String type = error.getClass().getSimpleName();
        String message = error.getMessage();
        return message == null || message.isBlank() ? type : type + ": " + message;
    }

    abstract String currentScript();

    Optional<ScriptInstance.Progress> scriptProgress() {
        return Optional.empty();
    }

    RunProgress progress() {
        Optional<ScriptInstance.Progress> script = scriptProgress();
        Integer done = script.map(ScriptInstance.Progress::done).orElse(null);
        Integer target = script.map(ScriptInstance.Progress::target).orElse(null);
        return new RunProgress(stepNumber(), totalSteps(), currentScript(), done, target);
    }

    int stepNumber() {
        return 1;
    }

    int totalSteps() {
        return 1;
    }

    void cancel() {
    }

    void suspend() {
    }

    void resume() {
    }

    PolicyExemptions exemptions() {
        return PolicyExemptions.empty();
    }

    Optional<ScriptResult> result() {
        return Optional.empty();
    }
}
