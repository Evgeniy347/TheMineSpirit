package com.theminespirit.neoforge.movement;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

final class RouteJump {
    private static final double MIN_LONG_JUMP = 1.6;
    private static final double MAX_LONG_JUMP = 2.6;
    private static final double SPEED = 1.0;

    private RouteJump() {}

    static boolean tryLong(SpiritEntity spirit, BlockCoord cell) {
        BlockPos feet = spirit.blockPosition();
        if (cell.y() != feet.getY()) {
            return false;
        }
        int dx = Integer.signum(cell.x() - feet.getX());
        int dz = Integer.signum(cell.z() - feet.getZ());
        if (dx != 0 && dz != 0) {
            return false;
        }
        double distance = Math.hypot(spirit.getX() - (cell.x() + 0.5), spirit.getZ() - (cell.z() + 0.5));
        if (distance <= MIN_LONG_JUMP || distance > MAX_LONG_JUMP) {
            return false;
        }
        Level level = spirit.level();
        BlockPos gap = feet.offset(dx, 0, dz);
        BlockPos target = new BlockPos(cell.x(), cell.y(), cell.z());
        if (!clear(level, gap)
                || !clear(level, gap.above())
                || !clear(level, target)
                || !clear(level, target.above())) {
            return false;
        }
        BlockPos floor = target.below();
        BlockState below = level.getBlockState(floor);
        if (!below.isFaceSturdy(level, floor, Direction.UP)) {
            return false;
        }
        spirit.getNavigation().stop();
        spirit.getMoveControl().setWantedPosition(cell.x() + 0.5, cell.y(), cell.z() + 0.5, SPEED);
        if (spirit.onGround()) {
            spirit.getJumpControl().jump();
        }
        return true;
    }

    private static boolean clear(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty()
                && state.getFluidState().isEmpty();
    }
}
