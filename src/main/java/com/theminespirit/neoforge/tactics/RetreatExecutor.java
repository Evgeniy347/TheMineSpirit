package com.theminespirit.neoforge.tactics;

import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.adapter.RetreatPlan;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class RetreatExecutor {
    public static final double RETARGET_DISTANCE = 1.5;

    public enum Progress {
        MOVING,
        ARRIVED,
        DONE
    }

    private final SpiritEntity spirit;
    private final Approach approach;
    private final CombatActuator combat;
    private final WorldFactCollector collector;

    private RetreatPlan current;
    private Vec3 lastTarget;

    public RetreatExecutor(SpiritEntity spirit, Approach approach, CombatActuator combat, WorldFactCollector collector) {
        this.spirit = spirit;
        this.approach = approach;
        this.combat = combat;
        this.collector = collector;
    }

    public Progress step(RetreatPlan plan, Optional<LivingEntity> shootTarget) {
        if (!plan.equals(current)) {
            reset();
            current = plan;
        }
        return switch (plan) {
            case RetreatPlan.Walk walk -> walk(walk, shootTarget);
            case RetreatPlan.Pour ignored -> pour();
        };
    }

    public void reset() {
        approach.cancel();
        current = null;
        lastTarget = null;
    }

    private Progress walk(RetreatPlan.Walk walk, Optional<LivingEntity> shootTarget) {
        spirit.gear().holdNothing();
        if (walk.shooting() && spirit.gear().arrows() > 0) {
            spirit.gear().holdBow();
            shootTarget.ifPresent(combat::fireWhenReady);
        }
        Point point = walk.target();
        Vec3 target = new Vec3(point.x(), point.y(), point.z());
        if (spirit.position().distanceTo(target) <= walk.arrival()) {
            approach.cancel();
            return Progress.ARRIVED;
        }
        if (!approach.isActive() || lastTarget == null || lastTarget.distanceTo(target) > RETARGET_DISTANCE) {
            approach.begin(target, walk.arrival());
            lastTarget = target;
        }
        approach.tick();
        return Progress.MOVING;
    }

    private Progress pour() {
        approach.cancel();
        spirit.gear().holdNothing();
        if (spirit.isInWater()) {
            return Progress.DONE;
        }
        BlockPos feet = spirit.blockPosition();
        if (!(spirit.protectionGate().checkPlace(feet) instanceof BlockProtection.Allowed)) {
            return Progress.DONE;
        }
        if (!spirit.gear().pourWater(feet)) {
            return Progress.DONE;
        }
        collector.invalidate(feet);
        return Progress.DONE;
    }
}
