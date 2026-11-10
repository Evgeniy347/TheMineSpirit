package com.theminespirit.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class DigOutStep {
    private DigOutStep() {}

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
    }
}
