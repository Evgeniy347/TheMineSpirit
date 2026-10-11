package com.theminespirit.core.engine;

import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;

abstract class NodeExecutor {
    private ScriptStatus status = ScriptStatus.RUNNING;
    private String reason;

    static NodeExecutor create(TaskNode node, ScriptRegistry scripts, ConditionRegistry conditions) {
        return switch (node) {
            case ScriptCall call -> new ScriptExecutor(call, scripts);
            case SequenceNode sequence -> new SequenceExecutor(sequence, scripts, conditions);
            case RepeatNode repeat -> new RepeatExecutor(repeat, scripts, conditions);
            case FallbackNode fallback -> new FallbackExecutor(fallback, scripts, conditions);
            case ParallelNode parallel -> new ParallelExecutor(parallel, scripts, conditions);
            case ConditionNode condition -> new ConditionExecutor(condition, scripts, conditions);
        };
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

    final void fail(String message) {
        status = ScriptStatus.FAILED;
        reason = message;
    }

    abstract String currentScript();

    void cancel() {
    }
}
