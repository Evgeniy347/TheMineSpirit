package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildState;
import java.util.Map;

final class ClearState {
    record Restored(int removed, int skipped, int failed) {}

    private ClearState() {}

    static Map<String, Object> save(int removed, int skipped, boolean running) {
        return new BuildState(0, removed, skipped, 0, running).toMap();
    }

    static Restored restore(Map<String, Object> saved) {
        BuildState state = BuildState.fromMap(saved);
        return new Restored(state.removed(), state.skipped(), state.failed());
    }
}
