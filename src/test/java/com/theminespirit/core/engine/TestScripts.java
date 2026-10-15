package com.theminespirit.core.engine;

import java.util.Map;
import java.util.Optional;

final class TestScripts {
    private TestScripts() {
    }

    static ScriptFactory succeeding(int ticks) {
        return params -> new TimedInstance(ticks, ScriptStatus.SUCCEEDED);
    }

    static ScriptFactory failing(int ticks) {
        return params -> new TimedInstance(ticks, ScriptStatus.FAILED);
    }

    static ScriptFactory counting(int ticks) {
        return params -> new CountingInstance(ticks);
    }

    static ScriptFactory observing(int ticks, boolean observes) {
        return new ScriptFactory() {
            @Override
            public ScriptInstance create(Map<String, Object> params) {
                return new TimedInstance(ticks, ScriptStatus.SUCCEEDED);
            }

            @Override
            public boolean observesWhileTacticsOwns() {
                return observes;
            }
        };
    }

    static ScriptFactory resulting(int ticks, ScriptResult result) {
        return params -> new ResultingInstance(ticks, result);
    }

    static ScriptRegistry singleRegistry(String id, ScriptFactory factory) {
        return ScriptRegistry.of(Map.of(id, factory));
    }

    static ConditionRegistry condition(String check, boolean value) {
        return ConditionRegistry.of(Map.of(check, params -> value));
    }

    private static final class TimedInstance implements ScriptInstance {
        private final int total;
        private final ScriptStatus terminal;
        private int elapsed;

        TimedInstance(int ticks, ScriptStatus terminal) {
            this.total = ticks;
            this.terminal = terminal;
        }

        @Override
        public ScriptStatus tick() {
            elapsed++;
            return elapsed >= total ? terminal : ScriptStatus.RUNNING;
        }
    }

    private static final class CountingInstance implements ScriptInstance {
        private final int target;
        private int done;

        CountingInstance(int ticks) {
            this.target = ticks;
        }

        @Override
        public ScriptStatus tick() {
            done++;
            return done >= target ? ScriptStatus.SUCCEEDED : ScriptStatus.RUNNING;
        }

        @Override
        public Optional<Progress> progress() {
            return Optional.of(new Progress(Math.min(done, target), target));
        }
    }

    private static final class ResultingInstance implements ScriptInstance {
        private final int total;
        private final ScriptResult result;
        private int elapsed;

        ResultingInstance(int ticks, ScriptResult result) {
            this.total = ticks;
            this.result = result;
        }

        @Override
        public ScriptStatus tick() {
            elapsed++;
            return elapsed >= total ? ScriptStatus.SUCCEEDED : ScriptStatus.RUNNING;
        }

        @Override
        public Optional<ScriptResult> result() {
            return Optional.of(result);
        }
    }
}
