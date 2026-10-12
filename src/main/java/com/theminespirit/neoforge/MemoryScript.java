package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.perception.MemoryCall;
import com.theminespirit.core.perception.MemoryFullException;
import com.theminespirit.core.perception.MemoryOutcome;

public final class MemoryScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final MemoryCall call;
    private String failureReason;

    public MemoryScript(SpiritEntity spirit, MemoryCall call) {
        this.spirit = spirit;
        this.call = call;
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            failureReason = "дух исчез, не успев обратиться к памяти";
            return ScriptStatus.FAILED;
        }
        MemoryOutcome outcome;
        try {
            outcome = call.apply(spirit.memory(), spirit.level().dimension().location().toString());
        } catch (MemoryFullException e) {
            failureReason = e.getMessage();
            return ScriptStatus.FAILED;
        }
        spirit.updateMemory(outcome.store());
        spirit.updateFinding(outcome.finding());
        return ScriptStatus.SUCCEEDED;
    }

    @Override
    public String failureReason() {
        return failureReason;
    }
}
