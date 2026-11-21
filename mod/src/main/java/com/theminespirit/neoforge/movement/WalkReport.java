package com.theminespirit.neoforge.movement;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class WalkReport {
    private static final double CENTER = 0.5;

    private WalkReport() {}

    static String describe(SpiritEntity spirit, BlockCoord cell) {
        Level level = spirit.level();
        BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
        BlockState state = level.getBlockState(pos);
        BlockState support = level.getBlockState(pos.below());
        boolean water = state.getFluidState().is(FluidTags.WATER);
        boolean collision = !state.getCollisionShape(level, pos).isEmpty();
        boolean climbable = state.is(BlockTags.CLIMBABLE);
        boolean rising = cell.y() > spirit.blockPosition().getY();
        boolean noEscape = water && noDryExit(level, pos);
        double distance = horizontal(spirit, new Vec3(cell.x() + CENTER, cell.y(), cell.z() + CENTER));
        String code = "NO_PROGRESS";
        if (collision) {
            code = "CELL_BLOCKED";
        } else if (water) {
            code = "SWIM_STALLED";
        } else if (rising) {
            code = climbable ? "CLIMB_STALLED" : "JUMP_STALLED";
        }
        return ": тип=" + (water ? "SWIM" : "WALK")
                + ", блок=" + BlockStates.idOf(state)
                + ", опора=" + BlockStates.idOf(support)
                + ", collision=" + collision
                + ", climbable=" + climbable
                + ", onGround=" + spirit.onGround()
                + ", onClimbable=" + spirit.onClimbable()
                + ", дистанция=" + String.format(Locale.ROOT, "%.2f", distance)
                + ", точка=" + String.format(Locale.ROOT, "%.2f,%.2f,%.2f", spirit.getX(), spirit.getY(), spirit.getZ())
                + ", move="
                + String.format(
                        Locale.ROOT,
                        "%.2f,%.2f,%.2f",
                        spirit.getMoveControl().getWantedX(),
                        spirit.getMoveControl().getWantedY(),
                        spirit.getMoveControl().getWantedZ())
                + ", между=" + between(level, spirit, cell)
                + ", no_escape=" + noEscape
                + ", код=" + code;
    }

    private static String between(Level level, SpiritEntity spirit, BlockCoord cell) {
        BlockPos blocked =
                blocking(level, spirit.position(), new Vec3(cell.x() + CENTER, cell.y() + CENTER, cell.z() + CENTER));
        return blocked == null ? "-" : BlockStates.idOf(level.getBlockState(blocked)) + "@" + blocked.toShortString();
    }

    static @Nullable BlockPos blocking(Level level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        int steps = (int) Math.ceil(delta.length() / 0.25);
        for (int i = 1; i < steps; i++) {
            BlockPos pos = BlockPos.containing(from.add(delta.scale(i / (double) steps)));
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return pos;
            }
        }
        return null;
    }

    private static boolean noDryExit(Level level, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos near = pos.relative(side).above(dy);
                BlockState state = level.getBlockState(near);
                if (state.getFluidState().isEmpty()
                        && state.getCollisionShape(level, near).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double horizontal(SpiritEntity spirit, Vec3 target) {
        double dx = spirit.getX() - target.x;
        double dz = spirit.getZ() - target.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
