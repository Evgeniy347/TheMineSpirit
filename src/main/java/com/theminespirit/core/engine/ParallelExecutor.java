package com.theminespirit.core.engine;

import com.theminespirit.core.plan.ParallelMode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.policy.PolicyExemptions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class ParallelExecutor extends NodeExecutor {
    private static final String STEP_FAILED = "Шаг параллельного узла провалился";
    private static final String ALL_STEPS_FAILED = "Все шаги параллельного узла провалились";

    private final ParallelMode mode;
    private final List<TaskNode> steps;
    private final ScriptRegistry scripts;
    private final ConditionRegistry conditions;
    private final List<NodeExecutor> children = new ArrayList<>();
    private final ScriptStatus[] results;
    private final @Nullable String[] reasons;
    private final @Nullable Throwable[] causes;

    ParallelExecutor(ParallelNode node, ScriptRegistry scripts, ConditionRegistry conditions) {
        this.mode = node.mode();
        this.steps = node.steps();
        this.scripts = scripts;
        this.conditions = conditions;
        this.results = new ScriptStatus[steps.size()];
        this.reasons = new String[steps.size()];
        this.causes = new Throwable[steps.size()];
        Arrays.fill(results, ScriptStatus.RUNNING);
    }

    @Override
    ScriptStatus advance() {
        createChildren();
        tickRunningChildren();
        return evaluate();
    }

    private void createChildren() {
        if (!children.isEmpty()) {
            return;
        }
        for (TaskNode step : steps) {
            children.add(NodeExecutor.create(step, scripts, conditions));
        }
    }

    private void tickRunningChildren() {
        for (int i = 0; i < children.size(); i++) {
            if (results[i] == ScriptStatus.RUNNING) {
                results[i] = children.get(i).tick();
                if (results[i] == ScriptStatus.FAILED) {
                    reasons[i] = children.get(i).failureReason();
                    causes[i] = children.get(i).failureCause().orElse(null);
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
        for (int i = 0; i < children.size(); i++) {
            if (results[i] == ScriptStatus.FAILED) {
                cancelAll();
                fail(reasons[i] != null ? reasons[i] : STEP_FAILED, causes[i]);
                return ScriptStatus.FAILED;
            }
        }
        for (ScriptStatus result : results) {
            if (result == ScriptStatus.CANCELLED) {
                cancelAll();
                return ScriptStatus.CANCELLED;
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
        for (ScriptStatus result : results) {
            if (result == ScriptStatus.CANCELLED) {
                return ScriptStatus.CANCELLED;
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
        if (children.isEmpty()) {
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
    void suspend() {
        for (int i = 0; i < children.size(); i++) {
            if (results[i] == ScriptStatus.RUNNING) {
                children.get(i).suspend();
            }
        }
    }

    @Override
    void resume() {
        for (int i = 0; i < children.size(); i++) {
            if (results[i] == ScriptStatus.RUNNING) {
                children.get(i).resume();
            }
        }
    }

    @Override
    PolicyExemptions exemptions() {
        PolicyExemptions union = PolicyExemptions.empty();
        for (int i = 0; i < children.size(); i++) {
            if (results[i] == ScriptStatus.RUNNING) {
                union = union.union(children.get(i).exemptions());
            }
        }
        return union;
    }

    @Override
    TaskProgress buildProgress() {
        if (children.isEmpty()) {
            List<TaskProgress.ParallelChild> pending = new ArrayList<>(steps.size());
            for (int i = 0; i < steps.size(); i++) {
                pending.add(new TaskProgress.ParallelChild(ScriptStatus.RUNNING, null, null));
            }
            return new TaskProgress.ParallelAt(pending);
        }
        List<TaskProgress.ParallelChild> listed = new ArrayList<>(children.size());
        for (int i = 0; i < children.size(); i++) {
            TaskProgress child =
                    results[i] == ScriptStatus.RUNNING ? children.get(i).snapshot() : null;
            listed.add(new TaskProgress.ParallelChild(results[i], reasons[i], child));
        }
        return new TaskProgress.ParallelAt(listed);
    }

    @Override
    void applyRestore(TaskProgress progress) {
        TaskProgress.ParallelAt at = (TaskProgress.ParallelAt) progress;
        createChildren();
        for (int i = 0; i < children.size(); i++) {
            TaskProgress.ParallelChild child = at.children().get(i);
            if (child.status() == ScriptStatus.RUNNING) {
                if (child.child() != null) {
                    children.get(i).restore(child.child());
                }
            } else {
                children.get(i).forceTerminal(child.status(), child.reason());
            }
            results[i] = child.status();
            reasons[i] = child.reason();
        }
    }

    @Override
    @Nullable
    String currentScript() {
        if (children.isEmpty()) {
            return null;
        }
        for (int i = 0; i < children.size(); i++) {
            if (results[i] == ScriptStatus.RUNNING) {
                String script = children.get(i).currentScript();
                if (script != null) {
                    return script;
                }
            }
        }
        return null;
    }

    @Override
    Optional<ScriptInstance.Progress> scriptProgress() {
        if (children.isEmpty()) {
            return Optional.empty();
        }
        for (int i = 0; i < children.size(); i++) {
            if (results[i] == ScriptStatus.RUNNING) {
                Optional<ScriptInstance.Progress> progress = children.get(i).scriptProgress();
                if (progress.isPresent()) {
                    return progress;
                }
            }
        }
        return Optional.empty();
    }

    @Override
    boolean observesWhileTacticsOwns() {
        if (children.isEmpty()) {
            return false;
        }
        boolean observes = false;
        for (int i = 0; i < children.size(); i++) {
            if (results[i] == ScriptStatus.RUNNING) {
                if (!children.get(i).observesWhileTacticsOwns()) {
                    return false;
                }
                observes = true;
            }
        }
        return observes;
    }
}
