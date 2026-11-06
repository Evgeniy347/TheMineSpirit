package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.HarvestHiveParams;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;

public final class HarvestHiveScript implements ScriptInstance {
    private final HiveSession session;

    public HarvestHiveScript(SpiritEntity spirit, HarvestHiveParams params) {
        this.session = HiveSession.harvest(spirit, params.radius(), params.count());
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
    public void suspend() {
        session.suspend();
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
