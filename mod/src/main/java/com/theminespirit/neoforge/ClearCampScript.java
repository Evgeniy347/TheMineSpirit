package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.ClearCampParams;
import java.util.Map;
import java.util.Optional;

final class ClearCampScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private String failureReason;
    private boolean published;

    ClearCampScript(SpiritEntity spirit, ClearCampParams params) {
        this.spirit = spirit;
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            failureReason = "дух исчез, не успев снять лагерь";
            return ScriptStatus.FAILED;
        }
        spirit.clearCamp();
        published = true;
        return ScriptStatus.SUCCEEDED;
    }

    @Override
    public Optional<ScriptResult> result() {
        return !published ? Optional.empty() : Optional.of(new ScriptResult.Data(Map.of("camp", false)));
    }

    @Override
    public String failureReason() {
        return failureReason;
    }
}
