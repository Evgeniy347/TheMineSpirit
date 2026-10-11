package com.theminespirit.core.engine;

import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelMode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TaskRunner {
    private final NodeExecutor root;
    private RunStatus status = RunStatus.RUNNING;
    private String reason;

    private TaskRunner(NodeExecutor root) {
        this.root = root;
    }

    public static TaskRunner start(TaskNode plan, ScriptRegistry scripts, ConditionRegistry conditions) {
        Objects.requireNonNull(plan, "plan не может быть null: без дерева задач исполнять нечего");
        Objects.requireNonNull(scripts, "scripts не может быть null: без реестра движок не создаст скрипты");
        Objects.requireNonNull(conditions, "conditions не может быть null: без реестра условие не выберет ветку");
        return new TaskRunner(NodeExecutor.create(plan, scripts, conditions));
    }

    public RunStatus tick() {
        if (status != RunStatus.RUNNING) {
            return status;
        }
        ScriptStatus node = root.tick();
        if (node == ScriptStatus.SUCCEEDED) {
            status = RunStatus.SUCCEEDED;
        } else if (node == ScriptStatus.FAILED) {
            status = RunStatus.FAILED;
            reason = root.failureReason();
        }
        return status;
    }

    public RunStatus status() {
        return status;
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(reason);
    }

    public Optional<String> currentScript() {
        if (status != RunStatus.RUNNING) {
            return Optional.empty();
        }
        return Optional.ofNullable(root.currentScript());
    }

    public void cancel() {
        if (status != RunStatus.RUNNING) {
            return;
        }
        status = RunStatus.CANCELLED;
        root.cancel();
    }

    private abstract static class NodeExecutor {
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

    private static final class ScriptExecutor extends NodeExecutor {
        private final String scriptId;
        private final Map<String, Object> params;
        private final ScriptRegistry registry;
        private ScriptInstance instance;

        ScriptExecutor(ScriptCall call, ScriptRegistry registry) {
            this.scriptId = call.script();
            this.params = call.params();
            this.registry = registry;
        }

        @Override
        ScriptStatus advance() {
            try {
                if (instance == null) {
                    instance = createInstance();
                }
                ScriptStatus result = instance.tick();
                if (result != ScriptStatus.RUNNING) {
                    instance = null;
                }
                return result;
            } catch (UnknownScriptException error) {
                fail(error.getMessage());
                return ScriptStatus.FAILED;
            } catch (RuntimeException error) {
                fail("скрипт '" + scriptId + "' упал: " + error.getMessage());
                return ScriptStatus.FAILED;
            }
        }

        private ScriptInstance createInstance() {
            ScriptFactory factory = registry.find(scriptId).orElseThrow(() -> new UnknownScriptException(
                    "Неизвестный скрипт '" + scriptId + "': он не зарегистрирован в реестре исполнения"));
            return factory.create(params);
        }

        @Override
        String currentScript() {
            return instance == null ? null : scriptId;
        }

        @Override
        void cancel() {
            if (instance != null) {
                ScriptInstance live = instance;
                instance = null;
                live.cancel();
            }
        }
    }

    private static final class SequenceExecutor extends NodeExecutor {
        private final List<TaskNode> steps;
        private final ScriptRegistry scripts;
        private final ConditionRegistry conditions;
        private int index;
        private NodeExecutor current;

        SequenceExecutor(SequenceNode node, ScriptRegistry scripts, ConditionRegistry conditions) {
            this.steps = node.steps();
            this.scripts = scripts;
            this.conditions = conditions;
        }

        @Override
        ScriptStatus advance() {
            if (index >= steps.size()) {
                return ScriptStatus.SUCCEEDED;
            }
            if (current == null) {
                current = NodeExecutor.create(steps.get(index), scripts, conditions);
            }
            ScriptStatus child = current.tick();
            if (child == ScriptStatus.FAILED) {
                fail(current.failureReason());
                return ScriptStatus.FAILED;
            }
            if (child == ScriptStatus.SUCCEEDED) {
                index++;
                current = null;
                if (index >= steps.size()) {
                    return ScriptStatus.SUCCEEDED;
                }
            }
            return ScriptStatus.RUNNING;
        }

        @Override
        String currentScript() {
            return current == null ? null : current.currentScript();
        }

        @Override
        void cancel() {
            if (current != null) {
                current.cancel();
            }
        }
    }

    private static final class RepeatExecutor extends NodeExecutor {
        private final int times;
        private final TaskNode step;
        private final ScriptRegistry scripts;
        private final ConditionRegistry conditions;
        private int done;
        private NodeExecutor current;

        RepeatExecutor(RepeatNode node, ScriptRegistry scripts, ConditionRegistry conditions) {
            this.times = node.times();
            this.step = node.step();
            this.scripts = scripts;
            this.conditions = conditions;
        }

        @Override
        ScriptStatus advance() {
            if (done >= times) {
                return ScriptStatus.SUCCEEDED;
            }
            if (current == null) {
                current = NodeExecutor.create(step, scripts, conditions);
            }
            ScriptStatus child = current.tick();
            if (child == ScriptStatus.FAILED) {
                fail(current.failureReason());
                return ScriptStatus.FAILED;
            }
            if (child == ScriptStatus.SUCCEEDED) {
                done++;
                current = null;
                if (done >= times) {
                    return ScriptStatus.SUCCEEDED;
                }
            }
            return ScriptStatus.RUNNING;
        }

        @Override
        String currentScript() {
            return current == null ? null : current.currentScript();
        }

        @Override
        void cancel() {
            if (current != null) {
                current.cancel();
            }
        }
    }

    private static final class FallbackExecutor extends NodeExecutor {
        private final List<TaskNode> steps;
        private final ScriptRegistry scripts;
        private final ConditionRegistry conditions;
        private int index;
        private NodeExecutor current;
        private String lastReason;

        FallbackExecutor(FallbackNode node, ScriptRegistry scripts, ConditionRegistry conditions) {
            this.steps = node.steps();
            this.scripts = scripts;
            this.conditions = conditions;
        }

        @Override
        ScriptStatus advance() {
            if (index >= steps.size()) {
                fail(lastReason != null ? lastReason
                        : "Все ветки fallback провалились: ни один шаг не завершился успехом");
                return ScriptStatus.FAILED;
            }
            if (current == null) {
                current = NodeExecutor.create(steps.get(index), scripts, conditions);
            }
            ScriptStatus child = current.tick();
            if (child == ScriptStatus.SUCCEEDED) {
                return ScriptStatus.SUCCEEDED;
            }
            if (child == ScriptStatus.FAILED) {
                lastReason = current.failureReason();
                index++;
                current = null;
                if (index >= steps.size()) {
                    fail(lastReason != null ? lastReason
                            : "Все ветки fallback провалились: ни один шаг не завершился успехом");
                    return ScriptStatus.FAILED;
                }
            }
            return ScriptStatus.RUNNING;
        }

        @Override
        String currentScript() {
            return current == null ? null : current.currentScript();
        }

        @Override
        void cancel() {
            if (current != null) {
                current.cancel();
            }
        }
    }

    private static final class ParallelExecutor extends NodeExecutor {
        private final ParallelMode mode;
        private final List<TaskNode> steps;
        private final ScriptRegistry scripts;
        private final ConditionRegistry conditions;
        private NodeExecutor[] children;
        private ScriptStatus[] results;
        private String[] reasons;

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
                    fail(reasons[i] != null ? reasons[i] : "Шаг параллельного узла провалился");
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
            for (String candidate : reasons) {
                if (candidate != null) {
                    lastReason = candidate;
                }
            }
            fail(lastReason != null ? lastReason : "Все шаги параллельного узла провалились");
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

    private static final class ConditionExecutor extends NodeExecutor {
        private final String check;
        private final Map<String, Object> params;
        private final TaskNode thenStep;
        private final TaskNode elseStep;
        private final ScriptRegistry scripts;
        private final ConditionRegistry conditions;
        private boolean resolved;
        private NodeExecutor chosen;

        ConditionExecutor(ConditionNode node, ScriptRegistry scripts, ConditionRegistry conditions) {
            this.check = node.check();
            this.params = node.params();
            this.thenStep = node.thenStep();
            this.elseStep = node.elseStep();
            this.scripts = scripts;
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
                chosen = NodeExecutor.create(branch, scripts, conditions);
            }
            ScriptStatus child = chosen.tick();
            if (child == ScriptStatus.FAILED) {
                fail(chosen.failureReason());
                return ScriptStatus.FAILED;
            }
            return child;
        }

        @Override
        String currentScript() {
            return chosen == null ? null : chosen.currentScript();
        }

        @Override
        void cancel() {
            if (chosen != null) {
                chosen.cancel();
            }
        }
    }
}
