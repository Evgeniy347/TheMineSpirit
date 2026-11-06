package com.theminespirit.neoforge.combat;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.FleeParams;
import com.theminespirit.core.social.WaitCounter;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatTuning;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.ScriptTargetResolver;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;

public final class FleeScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final TargetSelector.Target hostile = TargetSelector.parse("hostile");
    private WaitCounter safety;
    private int ticks;
    private boolean applied;
    private String failureReason;
    private ScriptResult result;

    public FleeScript(SpiritEntity spirit, FleeParams params) {
        this.spirit = spirit;
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез, пока убегал");
        }
        if (!applied) {
            safety = WaitCounter.start(CatalogLimits.FLEE_SAFE_TICKS);
            spirit.tactics().applyDirective(CombatDirective.flee());
            applied = true;
        }
        ticks++;
        Optional<LivingEntity> threat = ScriptTargetResolver.resolve(spirit, hostile);
        boolean danger = threat.isPresent()
                && threat.get().position().distanceTo(spirit.position()) <= CombatTuning.DANGER_RADIUS;
        if (danger) {
            safety = WaitCounter.start(CatalogLimits.FLEE_SAFE_TICKS);
        } else if (safety.tick()) {
            return succeed();
        }
        if (ticks >= CatalogLimits.FLEE_TIMEOUT_TICKS) {
            return fail("не убежал за " + ticks + " тиков (тайм-аут " + CatalogLimits.FLEE_TIMEOUT_TICKS
                    + "), ближайшая угроза: "
                    + (threat.isPresent()
                            ? "на расстоянии " + threat.get().position().distanceTo(spirit.position())
                            : "нет"));
        }
        return ScriptStatus.RUNNING;
    }

    @Override
    public void cancel() {
        release();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Optional<ScriptResult> result() {
        return Optional.ofNullable(result);
    }

    private ScriptStatus succeed() {
        result = new ScriptResult.Data(Map.of(
                "mode",
                CombatDirective.flee().mode().name(),
                "target",
                "hostile",
                "ticks",
                ticks,
                "outcome",
                "escaped"));
        release();
        return ScriptStatus.SUCCEEDED;
    }

    private ScriptStatus fail(String reason) {
        release();
        failureReason = reason;
        return ScriptStatus.FAILED;
    }

    private void release() {
        if (applied) {
            spirit.tactics().clearDirective();
            applied = false;
        }
    }
}
