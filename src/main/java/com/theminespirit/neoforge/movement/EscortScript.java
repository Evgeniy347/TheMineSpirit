package com.theminespirit.neoforge.movement;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.movement.PathRefreshPolicy;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.EscortParams;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.ScriptTargetResolver;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;

public final class EscortScript implements ScriptInstance {
    private static final double MOVE_SPEED = 1.0;

    private final SpiritEntity spirit;
    private final EscortParams params;
    private final TargetSelector.Target selector;
    private final CombatDirective directive;
    private final PathRefreshPolicy pathRefresh = new PathRefreshPolicy();
    private LivingEntity ward;
    private int ticks;
    private boolean applied;
    private String failureReason;
    private ScriptResult result;

    public EscortScript(SpiritEntity spirit, EscortParams params) {
        this.spirit = spirit;
        this.params = params;
        this.selector = params.selector();
        this.directive = CombatDirective.defend(selector);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            release();
            return fail("дух исчез во время сопровождения '" + params.target() + "'");
        }
        if (ward == null) {
            Optional<LivingEntity> found = ScriptTargetResolver.resolve(spirit, selector);
            if (found.isEmpty()) {
                return fail("цель '" + params.target() + "' не найдена: сопровождать и охранять некого");
            }
            ward = found.get();
            spirit.tactics().applyDirective(directive);
            spirit.tactics().setWard(ward.getUUID());
            applied = true;
        }
        if (ward.isRemoved() || !ward.isAlive()) {
            return fail("цель '" + params.target() + "' погибла или пропала во время сопровождения");
        }
        if (!params.untilStop() && ++ticks > params.untilTicks()) {
            return succeed();
        }
        if (spirit.position().distanceTo(ward.position()) <= params.radius()) {
            spirit.getNavigation().stop();
            return ScriptStatus.RUNNING;
        }
        BlockCoord targetCell = cellOf(ward);
        String refusal = TravelCheck.refusal(spirit, targetCell);
        if (refusal != null) {
            return fail("цель вышла за границы, куда духу нельзя: " + refusal);
        }
        if (pathRefresh.shouldRefresh(
                ward.getX(), ward.getY(), ward.getZ(), spirit.getNavigation().isDone())) {
            spirit.getNavigation().moveTo(ward, MOVE_SPEED);
            spirit.countPathBuild();
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
                "target", params.target(),
                "radius", params.radius(),
                "ticks", ticks));
        release();
        return ScriptStatus.SUCCEEDED;
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        release();
        return ScriptStatus.FAILED;
    }

    private void release() {
        if (applied) {
            spirit.tactics().clearDirective();
            applied = false;
        }
    }

    private static BlockCoord cellOf(LivingEntity target) {
        return new BlockCoord(
                target.blockPosition().getX(),
                target.blockPosition().getY(),
                target.blockPosition().getZ());
    }
}
