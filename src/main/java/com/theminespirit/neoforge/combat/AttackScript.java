package com.theminespirit.neoforge.combat;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.AttackParams;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritTargets;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;

public final class AttackScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final String targetText;
    private final TargetSelector.Target selector;
    private LivingEntity target;
    private int ticks;
    private boolean applied;
    private String failureReason;

    public AttackScript(SpiritEntity spirit, AttackParams params) {
        this.spirit = spirit;
        this.targetText = params.target();
        this.selector = TargetSelector.parse(params.target());
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез, пока атаковал '" + targetText + "'");
        }
        if (!applied) {
            spirit.tactics().applyDirective(CombatDirective.attack(selector));
            applied = true;
        }
        ticks++;
        Optional<LivingEntity> found = SpiritTargets.resolve(spirit, selector);
        if (found.isPresent()) {
            target = found.get();
            spirit.tactics().setForcedTarget(target);
        }
        if (target == null) {
            return fail("цель '" + targetText + "' не найдена рядом: атаковать нечего");
        }
        if (target.isDeadOrDying() || target.isRemoved()) {
            return succeed();
        }
        if (found.isEmpty()) {
            return fail("цель '" + targetText + "' пропала из поля зрения: атака сорвана");
        }
        if (ticks >= CatalogLimits.ATTACK_TIMEOUT_TICKS) {
            return fail("цель '" + targetText + "' не убита за " + ticks + " тиков (тайм-аут "
                    + CatalogLimits.ATTACK_TIMEOUT_TICKS + "), здоровье: " + target.getHealth());
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
