package com.theminespirit.neoforge.tactics;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.adapter.RetreatPlan;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.BlockBreaker;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RetreatExecutor {
    public static final int PLACE_INTERVAL_TICKS = 2;
    public static final double RETARGET_DISTANCE = 1.5;
    public static final double PILLAR_CLEARANCE = 1.0;

    public enum Progress {
        MOVING,
        ARRIVED,
        BUILDING,
        DONE
    }

    private final SpiritEntity spirit;
    private final Approach approach;
    private final CombatActuator combat;
    private final WorldFactCollector collector;

    private RetreatPlan current;
    private Vec3 lastTarget;
    private int lastPlaceTick = CombatActuator.NEVER;
    private int pillarBaseY;
    private int pillarBuilt;
    private int placed;

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
            pillarBaseY = spirit.blockPosition().getY();
        }
        return switch (plan) {
            case RetreatPlan.Walk walk -> walk(walk, shootTarget);
            case RetreatPlan.Wall wall -> wall(wall);
            case RetreatPlan.Tower tower -> tower(tower);
        };
    }

    public void reset() {
        approach.cancel();
        current = null;
        lastTarget = null;
        pillarBuilt = 0;
    }

    public int placedBlocks() {
        return placed;
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

    private Progress wall(RetreatPlan.Wall wall) {
        approach.cancel();
        Level level = spirit.level();
        boolean pending = false;
        for (BlockCoord cell : wall.cells()) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (!level.getBlockState(pos).isAir()) {
                continue;
            }
            pending = true;
            if (spirit.gear().blocks() == 0) {
                return Progress.DONE;
            }
            if (canPlaceNow(pos) && place(pos)) {
                return Progress.BUILDING;
            }
        }
        return pending ? Progress.BUILDING : Progress.DONE;
    }

    private Progress tower(RetreatPlan.Tower tower) {
        approach.cancel();
        if (pillarBuilt >= tower.height() || spirit.gear().blocks() == 0) {
            return Progress.DONE;
        }
        BlockPos cell = new BlockPos(spirit.blockPosition().getX(), pillarBaseY + pillarBuilt,
                spirit.blockPosition().getZ());
        if (spirit.getY() >= cell.getY() + PILLAR_CLEARANCE && canPlaceNow(cell) && place(cell)) {
            pillarBuilt++;
            return Progress.BUILDING;
        }
        if (spirit.onGround()) {
            spirit.getJumpControl().jump();
        }
        return Progress.BUILDING;
    }

    private boolean canPlaceNow(BlockPos pos) {
        if (spirit.tickCount - lastPlaceTick < PLACE_INTERVAL_TICKS) {
            return false;
        }
        if (spirit.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > BlockBreaker.REACH) {
            return false;
        }
        return spirit.level().getBlockState(pos).isAir()
                && spirit.level().getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty()
                && spirit.protectionGate().checkPlace(pos) instanceof BlockProtection.Allowed;
    }

    private boolean place(BlockPos pos) {
        if (!spirit.gear().takeBlock(pos)) {
            return false;
        }
        lastPlaceTick = spirit.tickCount;
        placed++;
        collector.invalidate(pos);
        return true;
    }
}
