package com.theminespirit.neoforge.combat;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.movement.DisengageRoute;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.DisengageParams;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.ScriptTargetResolver;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;

public final class DisengageScript implements ScriptInstance {
    public static final int REFRESH_TICKS = 10;

    private final SpiritEntity spirit;
    private final String targetText;
    private final TargetSelector.Target selector;
    private final int distance;
    private boolean applied;
    private int ticks;
    private String failureReason;
    private ScriptResult result;

    public DisengageScript(SpiritEntity spirit, DisengageParams params) {
        this.spirit = spirit;
        this.targetText = params.target();
        this.selector = params.selector();
        this.distance = params.distance();
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез, пока выходил из боя");
        }
        ticks++;
        Optional<LivingEntity> threat = ScriptTargetResolver.resolve(spirit, selector);
        if (threat.isEmpty()) {
            return succeed("no_enemy");
        }
        if (!applied || ticks % REFRESH_TICKS == 0) {
            withdrawFrom(threat.get());
        }
        applied = true;
        if (DisengageRoute.reached(self(), point(threat.get()), distance)) {
            return succeed("withdrawn");
        }
        if (ticks >= CatalogLimits.DISENGAGE_TIMEOUT_TICKS) {
            return fail("не вышел из боя за " + ticks + " тиков (тайм-аут "
                    + CatalogLimits.DISENGAGE_TIMEOUT_TICKS + "): ближайший враг в " + round(threat.get()) + " блоках");
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

    private void withdrawFrom(LivingEntity threat) {
        Point destination = DisengageRoute.away(self(), point(threat), distance);
        spirit.tactics().applyDirective(CombatDirective.retreat(destination));
    }

    private Point self() {
        return new Point(spirit.getX(), spirit.getY(), spirit.getZ());
    }

    private static Point point(LivingEntity entity) {
        return new Point(entity.getX(), entity.getY(), entity.getZ());
    }

    private double round(LivingEntity threat) {
        double dx = spirit.getX() - threat.getX();
        double dz = spirit.getZ() - threat.getZ();
        return Math.round(Math.hypot(dx, dz) * 10.0) / 10.0;
    }

    private ScriptStatus succeed(String outcome) {
        result = new ScriptResult.Data(Map.of(
                "mode", CombatDirective.flee().mode().name(),
                "target", targetText,
                "distance", distance,
                "ticks", ticks,
                "outcome", outcome));
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
