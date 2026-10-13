package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.ClearAreaParams;
import java.util.Map;
import java.util.Optional;

final class ClearAreaScript implements ScriptInstance {
    private final ClearAreaSession session;

    ClearAreaScript(SpiritEntity spirit, ClearAreaParams params) {
        this.session = new ClearAreaSession(spirit,
                BuildBox.of(params.x1(), params.y1(), params.z1(), params.x2(), params.y2(), params.z2()));
    }

    ClearAreaScript(ClearAreaSession session) {
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
    public Optional<Progress> progress() {
        return Optional.of(session.progress());
    }

    @Override
    public void cancel() {
        session.cancel();
    }

    @Override
    public void suspend() {
        session.suspend();
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
