package com.theminespirit.core.engine;

import com.theminespirit.core.policy.PolicyExemptions;
import java.util.Map;

public interface ScriptInstance {
    ScriptStatus tick();

    default void cancel() {
    }

    default void suspend() {
    }

    default void resume() {
    }

    default PolicyExemptions policyExemptions() {
        return PolicyExemptions.empty();
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
