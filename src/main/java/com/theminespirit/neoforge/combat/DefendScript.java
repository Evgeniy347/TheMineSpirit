package com.theminespirit.neoforge.combat;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.DefendParams;
import com.theminespirit.core.social.WaitCounter;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.ScriptTargetResolver;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;

public final class DefendScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final String targetText;
    private final TargetSelector.Target selector;
    private final CombatDirective directive;
    private LivingEntity ward;
    private WaitCounter safety;
    private float lastHealth;
    private int ticks;
    private boolean applied;
    private String failureReason;
    private ScriptResult result;

    public DefendScript(SpiritEntity spirit, DefendParams params) {
        this.spirit = spirit;
        this.targetText = params.target();
        this.selector = TargetSelector.parse(params.target());
        this.directive = CombatDirective.defend(selector);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез, пока защищал '" + targetText + "'");
        }
        if (!applied) {
            Optional<LivingEntity> found = ScriptTargetResolver.resolve(spirit, selector);
            if (found.isEmpty()) {
                return fail("подопечный '" + targetText + "' не найден: защищать некого");
            }
            ward = found.get();
            lastHealth = ward.getHealth();
            safety = WaitCounter.start(CatalogLimits.DEFEND_SAFE_TICKS);
            spirit.tactics().setWard(ward.getUUID());
            spirit.tactics().applyDirective(directive);
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
    public boolean observesWhileTacticsOwns() {
        return true;
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
                "outcome", "ward_safe"));
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
