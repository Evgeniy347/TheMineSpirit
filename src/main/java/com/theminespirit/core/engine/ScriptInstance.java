package com.theminespirit.core.engine;

import java.util.Map;

public interface ScriptInstance {
    ScriptStatus tick();

    default void cancel() {
    }

    default String failureReason() {
        return null;
    }

    default Map<String, Object> saveState() {
        return Map.of();
    }

    default void restoreState(Map<String, Object> state) {
    }
}
