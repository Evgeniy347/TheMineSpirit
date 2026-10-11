package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.GatherParams;

final class GatherScript implements ScriptInstance {
    private final HarvestSession session;

    GatherScript(SpiritEntity spirit, GatherParams params) {
        this.session = HarvestSession.gather(spirit, params.block(), params.count(), params.radius());
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
}