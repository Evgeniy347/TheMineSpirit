package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.BuildFarmParams;
import java.util.Map;
import java.util.Optional;

final class BuildFarmScript implements ScriptInstance {
    private final BuildFarmSession session;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;

    BuildFarmScript(SpiritEntity spirit, BuildFarmParams params) {
        this.session = new BuildFarmSession(spirit, params.plot(), params.crop());
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        outcome = switch (session.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case SUCCEEDED -> ScriptStatus.SUCCEEDED;
            case FAILED -> {
                failureReason = session.failureReason();
                yield ScriptStatus.FAILED;
            }
        };
        return outcome;
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(session.progressDone(), Math.max(session.target(), 1)));
    }

    @Override
    public Optional<ScriptResult> result() {
        return outcome == ScriptStatus.SUCCEEDED
                ? Optional.of(new ScriptResult.Data(session.resultData()))
                : Optional.empty();
    }

    @Override
    public void cancel() {
        session.cancel();
    }

    @Override
    public String failureReason() {
        return failureReason;
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
