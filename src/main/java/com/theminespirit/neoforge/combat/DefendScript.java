package com.theminespirit.neoforge.combat;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.DefendParams;
import com.theminespirit.core.social.WaitCounter;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritTargets;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;

public final class DefendScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final String targetText;
    private final TargetSelector.Target selector;
    private LivingEntity ward;
    private WaitCounter safety;
    private float lastHealth;
    private int ticks;
    private boolean applied;
    private String failureReason;

    public DefendScript(SpiritEntity spirit, DefendParams params) {
        this.spirit = spirit;
        this.targetText = params.target();
        this.selector = TargetSelector.parse(params.target());
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез, пока защищал '" + targetText + "'");
        }
        if (!applied) {
            Optional<LivingEntity> found = SpiritTargets.resolve(spirit, selector);
            if (found.isEmpty()) {
                return fail("подопечный '" + targetText + "' не найден: защищать некого");
            }
            ward = found.get();
            lastHealth = ward.getHealth();
            safety = WaitCounter.start(CatalogLimits.DEFEND_SAFE_TICKS);
            spirit.tactics().setWard(ward.getUUID());
            spirit.tactics().applyDirective(CombatDirective.defend(selector));
            applied = true;
        }
        ticks++;
        if (ward.isRemoved() || !ward.isAlive()) {
            return fail("подопечный '" + targetText + "' погиб или пропал под защитой");
        }
        float health = ward.getHealth();
        if (health < lastHealth) {
            safety = WaitCounter.start(CatalogLimits.DEFEND_SAFE_TICKS);
        }
        lastHealth = health;
        if (safety.tick()) {
            return succeed();
        }
        if (ticks >= CatalogLimits.DEFEND_TIMEOUT_TICKS) {
            return fail("подопечный '" + targetText + "' под угрозой " + ticks
                    + " тиков (тайм-аут " + CatalogLimits.DEFEND_TIMEOUT_TICKS + ")");
        }
        return ScriptStatus.RUNNING;
    }

    @Override
    public void cancel() {
        release();
    }

    @Override
    public void suspend() {
        release();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    private ScriptStatus succeed() {
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
