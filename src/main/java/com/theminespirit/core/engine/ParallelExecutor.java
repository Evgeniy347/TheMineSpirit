package com.theminespirit.core.engine;

import com.theminespirit.core.plan.ParallelMode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;

final class ParallelExecutor extends NodeExecutor {
    private static final String STEP_FAILED = "Шаг параллельного узла провалился";
    private static final String ALL_STEPS_FAILED = "Все шаги параллельного узла провалились";

    private final ParallelMode mode;
    private final List<TaskNode> steps;
    private final ScriptRegistry scripts;
    private final ConditionRegistry conditions;
    private NodeExecutor[] children;
    private ScriptStatus[] results;
    private String[] reasons;
    private Throwable[] causes;

    ParallelExecutor(ParallelNode node, ScriptRegistry scripts, ConditionRegistry conditions) {
        this.mode = node.mode();
        this.steps = node.steps();
        this.scripts = scripts;
        this.conditions = conditions;
    }

    @Override
    ScriptStatus advance() {
        createChildren();
        tickRunningChildren();
        return evaluate();
    }

    private void createChildren() {
        if (children != null) {
            return;
        }
        children = new NodeExecutor[steps.size()];
        results = new ScriptStatus[steps.size()];
        reasons = new String[steps.size()];
        causes = new Throwable[steps.size()];
        for (int i = 0; i < steps.size(); i++) {
            children[i] = NodeExecutor.create(steps.get(i), scripts, conditions);
            results[i] = ScriptStatus.RUNNING;
        }
    }

    private void tickRunningChildren() {
        for (int i = 0; i < children.length; i++) {
            if (results[i] == ScriptStatus.RUNNING) {
                results[i] = children[i].tick();
                if (results[i] == ScriptStatus.FAILED) {
                    reasons[i] = children[i].failureReason();
                    causes[i] = children[i].failureCause().orElse(null);
                }
            }
        }
    }

    private ScriptStatus evaluate() {
        if (mode == ParallelMode.ALL) {
            return evaluateAll();
        }
        return evaluateAny();
    }

    private ScriptStatus evaluateAll() {
        for (int i = 0; i < children.length; i++) {
            if (results[i] == ScriptStatus.FAILED) {
                cancelAll();
                fail(reasons[i] != null ? reasons[i] : STEP_FAILED, causes[i]);
                return ScriptStatus.FAILED;
            }
        }
        for (ScriptStatus result : results) {
            if (result == ScriptStatus.RUNNING) {
                return ScriptStatus.RUNNING;
            }
        }
        return ScriptStatus.SUCCEEDED;
    }

    private ScriptStatus evaluateAny() {
        for (ScriptStatus result : results) {
            if (result == ScriptStatus.SUCCEEDED) {
                cancelAll();
                return ScriptStatus.SUCCEEDED;
            }
        }
        for (ScriptStatus result : results) {
            if (result == ScriptStatus.RUNNING) {
                return ScriptStatus.RUNNING;
            }
        }
        String lastReason = null;
        Throwable lastCause = null;
        for (int i = 0; i < reasons.length; i++) {
            if (reasons[i] != null) {
                lastReason = reasons[i];
                lastCause = causes[i];
            }
        }
        fail(lastReason != null ? lastReason : ALL_STEPS_FAILED, lastCause);
        return ScriptStatus.FAILED;
    }

    private void cancelAll() {
        if (children == null) {
            return;
        }
        for (NodeExecutor child : children) {
            child.cancel();
        }
    }

    @Override
    void cancel() {
        cancelAll();
    }

    @Override
    String currentScript() {
        if (children == null) {
            return null;
        }
        for (int i = 0; i < children.length; i++) {
            if (results[i] == ScriptStatus.RUNNING) {
                String script = children[i].currentScript();
                if (script != null) {
                    return script;
                }
            }
        }
        return null;
    }
}
