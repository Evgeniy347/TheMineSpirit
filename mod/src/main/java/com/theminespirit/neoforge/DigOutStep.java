package com.theminespirit.neoforge;

import com.theminespirit.core.movement.StairJump;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

final class DigOutStep {
    private DigOutStep() {}

    static Direction towardTarget(BlockPos feet, int targetX, int targetZ) {
        int dx = targetX - feet.getX();
        int dz = targetZ - feet.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    static void moveTo(SpiritEntity spirit, BlockPos cell) {
        if (spirit.isInWater()) {
            spirit.getNavigation().stop();
            spirit.getMoveControl().setWantedPosition(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 1.0);
            Vec3 motion = spirit.getDeltaMovement();
            spirit.setDeltaMovement(motion.x, Math.max(motion.y, 0.2), motion.z);
            spirit.getJumpControl().jump();
            return;
        }
        spirit.getNavigation().stop();
        spirit.getMoveControl().setWantedPosition(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 1.0);
        if (StairJump.needed(
                cell.getY() - spirit.blockPosition().getY(), spirit.onGround(), spirit.horizontalCollision)) {
            spirit.getJumpControl().jump();
        }
    }
}
