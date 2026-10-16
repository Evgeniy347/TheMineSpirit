package com.theminespirit.neoforge.hunger;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.EatParams;
import com.theminespirit.neoforge.SpiritEntity;

public final class EatScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final EatParams params;
    private ScriptStatus status = ScriptStatus.RUNNING;
    private String reason;

    public EatScript(SpiritEntity spirit, EatParams params) {
        this.spirit = spirit;
        this.params = params;
    }

    @Override
    public ScriptStatus tick() {
        if (status != ScriptStatus.RUNNING) {
            return status;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез до еды");
        }
        boolean ate = params.item().map(id -> spirit.hunger().eat(id))
                .orElseGet(() -> spirit.hunger().eatBest());
        if (!ate) {
            return fail(params.item()
                    .map(id -> "в инвентаре нет еды '" + id + "': есть нечего")
                    .orElse("в инвентаре нет еды: есть нечего"));
        }
        return succeed();
    }

    @Override
    public void cancel() {
    }

    @Override
    public String failureReason() {
        return reason;
    }

    private ScriptStatus succeed() {
        status = ScriptStatus.SUCCEEDED;
        return status;
    }

    private ScriptStatus fail(String message) {
        status = ScriptStatus.FAILED;
        reason = message;
        return status;
    }
}
