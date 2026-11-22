package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import java.util.Map;

final class ClearProgress {
    private ClearProgress() {}

    static ScriptInstance.Progress progress(ClearAreaSession session) {
        int total = Math.max(session.planned, 1);
        return new ScriptInstance.Progress(Math.min(session.removed + session.skippedTotal(), total), total);
    }

    static Map<String, Object> save(ClearAreaSession session) {
        return ClearState.save(
                session.removed, session.skippedTotal(), session.outcome() == ClearAreaSession.Outcome.RUNNING);
    }

    static void restore(ClearAreaSession session, Map<String, Object> saved) {
        ClearState.Restored state = ClearState.restore(saved);
        session.removed = Math.max(session.removed, state.removed());
        session.restoredSkipped = Math.max(session.restoredSkipped, state.skipped());
        for (int i = 0; i < state.removed(); i++) {
            session.tally.removed();
        }
        for (int i = 0; i < state.skipped(); i++) {
            session.tally.skipped();
        }
        for (int i = 0; i < state.failed(); i++) {
            session.tally.failed();
        }
    }
}
