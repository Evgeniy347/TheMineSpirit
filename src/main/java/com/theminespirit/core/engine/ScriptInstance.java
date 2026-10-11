package com.theminespirit.core.engine;

public interface ScriptInstance {
    ScriptStatus tick();

    default void cancel() {
    }
}
