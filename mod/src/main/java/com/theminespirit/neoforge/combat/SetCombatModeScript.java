package com.theminespirit.neoforge.combat;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.SetCombatModeParams;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.Optional;

public final class SetCombatModeScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final CombatMode mode;
    private ScriptStatus outcome = ScriptStatus.RUNNING;

    public SetCombatModeScript(SpiritEntity spirit, SetCombatModeParams params) {
        this.spirit = spirit;
        this.mode = params.mode();
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        spirit.tactics().setCombatMode(mode);
        outcome = ScriptStatus.SUCCEEDED;
        return outcome;
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(0, 1));
    }

    @Override
    public Optional<ScriptResult> result() {
        return Optional.of(new ScriptResult.Data(Map.of("mode", mode.id())));
    }

    @Override
    public String failureReason() {
        return null;
    }
}
