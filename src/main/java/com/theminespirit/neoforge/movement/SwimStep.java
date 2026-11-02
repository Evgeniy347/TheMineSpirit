package com.theminespirit.neoforge.movement;

import com.theminespirit.core.movement.WaterTraverse;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.PathMode;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;

final class SwimStep {
    private static final int AIR_THRESHOLD = 120;
    private static final double SPEED = 1.0;
    private final SpiritEntity spirit;
    private final Approach approach;
    private BlockCoord planned;

    SwimStep(SpiritEntity spirit) {
        this.spirit = spirit;
        this.approach = new Approach(spirit);
    }

    static boolean far(SpiritEntity spirit, BlockCoord cell) {
        double dx = Math.abs(spirit.getX() - (cell.x() + 0.5));
        double dz = Math.abs(spirit.getZ() - (cell.z() + 0.5));
        return dx > 1.6 || dz > 1.6;
    }

    boolean tick(BlockCoord cell) {
        if (WaterTraverse.arrived(spirit.getX(), spirit.getY(), spirit.getZ(), cell)) {
            return true;
        }
        boolean targetWater = water(new BlockPos(cell.x(), cell.y(), cell.z()))
                || water(new BlockPos(cell.x(), cell.y() - 1, cell.z()));
        WaterTraverse.Phase phase = WaterTraverse.phase(spirit.isInWater(), targetWater);
        if (phase == WaterTraverse.Phase.ENTER || !spirit.isInWater()) {
            entry(cell);
            return false;
        }
        approach.cancel();
        planned = null;
        spirit.getNavigation().stop();
        spirit.getMoveControl().setWantedPosition(cell.x() + 0.5, cell.y(), cell.z() + 0.5, SPEED);
        surfaceForAir();
        return false;
    }

    private void entry(BlockCoord cell) {
        int targetY = water(new BlockPos(cell.x(), cell.y() - 1, cell.z())) ? cell.y() - 1 : cell.y();
        spirit.getNavigation().stop();
        spirit.getMoveControl().setWantedPosition(cell.x() + 0.5, targetY, cell.z() + 0.5, SPEED);
        double dx = cell.x() + 0.5 - spirit.getX();
        double dz = cell.z() + 0.5 - spirit.getZ();
        Vec3 motion = spirit.getDeltaMovement();
        spirit.setDeltaMovement(motion.x + Math.signum(dx) * 0.1, -0.1, motion.z + Math.signum(dz) * 0.1);
        if (spirit.onGround() || spirit.horizontalCollision) {
            spirit.getJumpControl().jump();
        }
    }

    boolean exit(BlockCoord cell) {
        if (spirit.isInWater()) {
            spirit.getNavigation().stop();
            spirit.getMoveControl().setWantedPosition(cell.x() + 0.5, cell.y(), cell.z() + 0.5, SPEED);
            if (spirit.isUnderWater() || spirit.horizontalCollision || spirit.getAirSupply() < AIR_THRESHOLD) {
                spirit.getJumpControl().jump();
            }
            return WaterTraverse.arrived(spirit.getX(), spirit.getY(), spirit.getZ(), cell);
        }
        return approach(cell);
    }

    private boolean approach(BlockCoord cell) {
        if (!cell.equals(planned)) {
            approach.cancel();
            approach.begin(new Vec3(cell.x() + 0.5, cell.y(), cell.z() + 0.5), 0.8, PathMode.SWIM);
            planned = cell;
        }
        return approach.tick() == Approach.State.ARRIVED;
    }

    private void surfaceForAir() {
        if (spirit.isUnderWater() || spirit.horizontalCollision || spirit.getAirSupply() < AIR_THRESHOLD) {
            spirit.getJumpControl().jump();
        }
    }

    private boolean water(BlockPos pos) {
        return spirit.level().getFluidState(pos).is(FluidTags.WATER);
    }
}
