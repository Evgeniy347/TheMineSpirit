package com.theminespirit.core.engine;

import com.theminespirit.core.policy.PolicyExemptions;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public interface ScriptInstance {
    record Progress(int done, int target) {
        public Progress {
            if (done < 0) {
                throw new IllegalArgumentException("done=" + done + " отрицателен: сделанного не бывает меньше нуля");
            }
            if (target < 1) {
                throw new IllegalArgumentException(
                        "target=" + target + " меньше 1: счётный скрипт стремится к положительной цели");
            }
        }
    }

    ScriptStatus tick();

    default Optional<Progress> progress() {
        return Optional.empty();
    }

    default void cancel() {}

    default void suspend() {}

    default void resume() {}

    default PolicyExemptions policyExemptions() {
        return PolicyExemptions.empty();
    }

    default @Nullable String failureReason() {
        return null;
    }

    default Optional<ScriptResult> result() {
        return Optional.empty();
    }

    default Map<String, Object> saveState() {
        return Map.of();
    }

    default void restoreState(Map<String, Object> state) {}
}
