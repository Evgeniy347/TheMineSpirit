package com.theminespirit.neoforge.policy;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.ReflexAction;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.world.phys.Vec3;

final class ReflexExecutor {
    static final double HOP_DISTANCE = 2.5;
    static final double RUN_SPEED = 1.3;
    static final double LAVA_PUSH = 0.15;

    private BlockCoord lastTarget;

    void execute(SpiritEntity spirit, ReflexAction action) {
        switch (action) {
            case ReflexAction.Flee flee -> moveTo(spirit, flee.cell());
            case ReflexAction.ExitWater exit -> {
                moveTo(spirit, exit.cell());
                swimUp(spirit);
            }
            case ReflexAction.StepOutOfPortal step -> moveTo(spirit, step.cell());
            case ReflexAction.ReturnToZone back -> moveTo(spirit, back.cell());
            case ReflexAction.Surface surface -> swimUp(spirit);
            case ReflexAction.StopMoving stop -> release(spirit);
            case ReflexAction.Eat eat -> spirit.hunger().eatBest();
        }
    }

    void release(SpiritEntity spirit) {
        spirit.getNavigation().stop();
        lastTarget = null;
    }

    private void moveTo(SpiritEntity spirit, BlockCoord cell) {
        Vec3 target = new Vec3(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
        if (spirit.position().distanceTo(target) <= HOP_DISTANCE) {
            spirit.getNavigation().stop();
            spirit.getMoveControl().setWantedPosition(target.x, target.y, target.z, RUN_SPEED);
            pushThroughLava(spirit, target);
            lastTarget = cell;
            return;
        }
        if (!cell.equals(lastTarget) || spirit.getNavigation().isDone()) {
            spirit.getNavigation().moveTo(target.x, target.y, target.z, RUN_SPEED);
            lastTarget = cell;
        }
    }

    private void pushThroughLava(SpiritEntity spirit, Vec3 target) {
        if (spirit.isInLava()) {
            Vec3 toward = new Vec3(target.x - spirit.getX(), 0, target.z - spirit.getZ());
            if (toward.lengthSqr() > 0) {
                spirit.setDeltaMovement(spirit.getDeltaMovement().add(toward.normalize().scale(LAVA_PUSH)));
            }
            spirit.getJumpControl().jump();
        }
    }

    private void swimUp(SpiritEntity spirit) {
        if (spirit.isInWater() || spirit.isInLava()) {
            spirit.getJumpControl().jump();
        }
    }
}
