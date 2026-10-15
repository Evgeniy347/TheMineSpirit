package com.theminespirit.neoforge.combat;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.AttackParams;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.ScriptTargetResolver;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;

public final class AttackScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final String targetText;
    private final TargetSelector.Target selector;
    private final CombatDirective directive;
    private LivingEntity target;
    private int ticks;
    private boolean applied;
    private String failureReason;
    private ScriptResult result;

    public AttackScript(SpiritEntity spirit, AttackParams params) {
        this.spirit = spirit;
        this.targetText = params.target();
        this.selector = TargetSelector.parse(params.target());
        this.directive = CombatDirective.attack(selector);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез, пока атаковал '" + targetText + "'");
        }
        if (!applied) {
            spirit.tactics().applyDirective(directive);
            applied = true;
        }
        ticks++;
        if (target != null && (target.isDeadOrDying() || target.isRemoved())) {
            return succeed();
        }
        if (target == null || true) {
            Optional<LivingEntity> found = ScriptTargetResolver.resolve(spirit, selector);
            if (found.isPresent()) {
                target = found.get();
                spirit.tactics().setForcedTarget(target);
            }
        }
        if (ticks >= CatalogLimits.ATTACK_TIMEOUT_TICKS) {
            return fail("цель '" + targetText + "' не убита за " + ticks + " тиков (тайм-аут "
                    + CatalogLimits.ATTACK_TIMEOUT_TICKS + ")"
                    + (target != null ? ", здоровье: " + target.getHealth() : ", цель так и не найдена"));
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
                "mode", directive.mode().name(),
                "target", targetText,
                "ticks", ticks,
                "outcome", "target_down"));
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
