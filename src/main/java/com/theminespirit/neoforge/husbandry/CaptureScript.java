package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.CaptureParams;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.Optional;

public final class CaptureScript implements ScriptInstance {
    private final CaptureSession session;

    public CaptureScript(SpiritEntity spirit, CaptureParams params) {
        this.session = new CaptureSession(spirit, params.species(), params.radius());
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
    public Optional<ScriptResult> result() {
        return session.outcome() == CaptureSession.Outcome.SUCCEEDED
                ? Optional.of(new ScriptResult.Data(session.resultData()))
                : Optional.empty();
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
