package com.theminespirit.neoforge.social;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.WaitParams;
import com.theminespirit.core.social.WaitCounter;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.Optional;

public final class WaitScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final int targetTicks;
    private WaitCounter counter;
    private String failureReason;
    private ScriptResult result;

    public WaitScript(SpiritEntity spirit, WaitParams params) {
        this.spirit = spirit;
        this.targetTicks = params.ticks();
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез, пока ждал " + targetTicks + " тиков");
        }
        if (counter == null) {
            counter = WaitCounter.start(targetTicks);
        }
        if (counter.tick()) {
            result = new ScriptResult.Data(Map.of("ticks", targetTicks));
            return ScriptStatus.SUCCEEDED;
        }
        return ScriptStatus.RUNNING;
    }

    @Override
    public void cancel() {
    }

    @Override
    public void suspend() {
    }

    @Override
    public Map<String, Object> saveState() {
        return counter == null ? WaitCounter.start(targetTicks).toMap() : counter.toMap();
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        counter = WaitCounter.fromMap(state);
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Optional<ScriptResult> result() {
        return Optional.ofNullable(result);
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        return ScriptStatus.FAILED;
    }
}
