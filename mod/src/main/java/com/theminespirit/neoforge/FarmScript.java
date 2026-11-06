package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import java.util.Map;

class FarmScript implements ScriptInstance {
    private final FarmSession session;

    FarmScript(FarmSession session) {
        this.session = session;
    }

    @Override
    public ScriptStatus tick() {
        return switch (session.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case SUCCEEDED -> ScriptStatus.SUCCEEDED;
            case FAILED -> ScriptStatus.FAILED;
        };
    }

    @Override
    public void cancel() {
        session.cancel();
    }

    @Override
    public String failureReason() {
        return session.failureReason();
    }

    @Override
    public Map<String, Object> saveState() {
        return session.saveState();
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        session.restoreState(state);
    }
}
