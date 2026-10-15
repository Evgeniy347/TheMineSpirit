package com.theminespirit.core.engine;

import java.util.Map;

@FunctionalInterface
public interface ScriptFactory {
    ScriptInstance create(Map<String, Object> params);

    default boolean observesWhileTacticsOwns() {
        return false;
    }
}
