package com.theminespirit.neoforge.movement;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.GotoBlockParams;
import com.theminespirit.neoforge.SpiritEntity;

public final class GotoBlockScript implements ScriptInstance {
    private final GotoBlockSession session;

    public GotoBlockScript(SpiritEntity spirit, GotoBlockParams params) {
        this.session = new GotoBlockSession(spirit, params.block(), params.radius(), params.count());
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
    public void suspend() {
        session.suspend();
    }

    @Override
    public String failureReason() {
        return session.failureReason();
    }
}
