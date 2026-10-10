package com.theminespirit.core.engine;

import java.util.Map;

final class TestScripts {
    private TestScripts() {
    }

    static ScriptFactory succeeding(int ticks) {
        return params -> new TimedInstance(ticks, ScriptStatus.SUCCEEDED);
    }

    static ScriptFactory failing(int ticks) {
        return params -> new TimedInstance(ticks, ScriptStatus.FAILED);
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
}
