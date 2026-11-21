package com.theminespirit.core.engine;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class TaskRunnerRestoreTestsSupport {
    static final String CHECK = "seen";

    static TaskSnapshot terminal(RunStatus status, String reason) {
        return new TaskSnapshot(TaskSnapshot.VERSION, status, reason, null, null);
    }

    static TaskRunner restore(TaskRunner runner, ScriptRegistry registry, ConditionRegistry conditions) {
        TaskSnapshot snapshot = runner.snapshot()
                .orElseThrow(() -> new AssertionError("работающая задача обязана отдавать снимок для восстановления"));
        TaskSnapshot decoded = TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(snapshot));
        return TaskRunner.restore(decoded, registry, conditions);
    }

    static RunStatus runToTerminal(TaskRunner runner) {
        for (int i = 0; i < 1000; i++) {
            RunStatus status = runner.tick();
            if (status != RunStatus.RUNNING) {
                return status;
            }
        }
        throw new AssertionError("план не завершился за 1000 тиков");
    }

    static TaskNode script(String id) {
        return new ScriptCall(id, Map.of());
    }

    static ScriptRegistry registry(NamedFactory... factories) {
        Map<String, ScriptFactory> byId = new LinkedHashMap<>();
        for (NamedFactory factory : factories) {
            byId.put(factory.id, factory);
        }
        return ScriptRegistry.of(byId);
    }

    static NamedFactory factory(String id, List<String> created, int ticks, ScriptStatus terminal, String reason) {
        return named(id, params -> {
            created.add(id);
            return new CountingInstance(ticks, terminal, reason);
        });
    }

    static NamedFactory named(String id, ScriptFactory delegate) {
        return new NamedFactory(id, delegate);
    }

    static ConditionRegistry conditions() {
        return ConditionRegistry.of(Map.of(CHECK, params -> true));
    }

    static final class NamedFactory implements ScriptFactory {
        final String id;
        final ScriptFactory delegate;

        NamedFactory(String id, ScriptFactory delegate) {
            this.id = id;
            this.delegate = delegate;
        }

        @Override
        public ScriptInstance create(Map<String, Object> params) {
            return delegate.create(params);
        }
    }

    static final class CountingInstance implements ScriptInstance {
        final int total;
        final ScriptStatus terminal;
        final String reason;
        int elapsed;

        CountingInstance(int total, ScriptStatus terminal, String reason) {
            this.total = total;
            this.terminal = terminal;
            this.reason = reason;
        }

        @Override
        public ScriptStatus tick() {
            elapsed++;
            return elapsed >= total ? terminal : ScriptStatus.RUNNING;
        }

        @Override
        public String failureReason() {
            return reason;
        }

        @Override
        public Map<String, Object> saveState() {
            return Map.of("elapsed", elapsed);
        }

        @Override
        public void restoreState(Map<String, Object> state) {
            Object value = state.get("elapsed");
            if (value instanceof Number number) {
                elapsed = number.intValue();
            }
        }
    }

    static final class StatefulInstance implements ScriptInstance {
        final List<Integer> restoredElapsed;
        int elapsed;

        StatefulInstance(List<Integer> restoredElapsed) {
            this.restoredElapsed = restoredElapsed;
        }

        @Override
        public ScriptStatus tick() {
            elapsed++;
            return elapsed >= 5 ? ScriptStatus.SUCCEEDED : ScriptStatus.RUNNING;
        }

        @Override
        public Map<String, Object> saveState() {
            return Map.of("elapsed", elapsed);
        }

        @Override
        public void restoreState(Map<String, Object> state) {
            Object value = state.get("elapsed");
            if (value instanceof Number number) {
                elapsed = number.intValue();
            }
            restoredElapsed.add(elapsed);
        }
    }
}
