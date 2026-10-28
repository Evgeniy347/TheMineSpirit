package com.theminespirit.neoforge.crafting;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.BrewParams;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BrewScript implements ScriptInstance {
    private static final String MADE_KEY = "brewed";
    private static final String FAILURE_KEY = "brewFailure";

    private final BrewParams params;
    private final BrewingSession session;
    private String failureReason;

    public BrewScript(SpiritEntity spirit, BrewParams params) {
        this.params = params;
        this.session = new BrewingSession(spirit, params.item(), params.count(), params.radius());
    }

    @Override
    public ScriptStatus tick() {
        BrewingSession.Outcome outcome = session.tick();
        return switch (outcome) {
            case RUNNING -> ScriptStatus.RUNNING;
            case SUCCEEDED -> ScriptStatus.SUCCEEDED;
            case FAILED -> {
                failureReason = session.failureReason();
                yield ScriptStatus.FAILED;
            }
        };
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
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(MADE_KEY, session.made());
        state.put("product", params.item());
        state.put("startedAt", session.startedAt());
        if (failureReason != null) {
            state.put(FAILURE_KEY, failureReason);
        }
        return state;
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        if (state.get(MADE_KEY) instanceof Number number) {
            session.restoreMade(number.intValue());
        }
        if (state.get(FAILURE_KEY) instanceof String reason) {
            failureReason = reason;
        }
    }
}
