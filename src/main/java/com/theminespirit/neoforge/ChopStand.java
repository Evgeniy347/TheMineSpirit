package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class ChopStand {
    private static final double DIAGONAL_PENALTY = 10_000;

    private ChopStand() {}

    static @Nullable BlockPos first(SpiritEntity spirit, Tree target) {
        Vec3 from = spirit.position();
        return best(spirit.level(), target, null, pos -> from.distanceToSqr(Vec3.atBottomCenterOf(pos)));
    }

    static @Nullable BlockPos second(SpiritEntity spirit, Tree target, List<BlockCoord> rest, @Nullable BlockPos used) {
        return best(spirit.level(), target, used, pos -> nearest(pos, rest));
    }

    private static @Nullable BlockPos best(
            Level level, Tree target, @Nullable BlockPos used, ToDoubleFunction<BlockPos> score) {
        int baseY = target.base().y();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockCoord log : target.logs()) {
            if (log.y() != baseY) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 1 && (dx != 0 || dz != 0); dy++) {
                        BlockPos pos = new BlockPos(log.x() + dx, baseY + dy, log.z() + dz);
                        if (pos.equals(used) || !isStandable(level, pos)) {
                            continue;
                        }
                        double value = score.applyAsDouble(pos) + (dx != 0 && dz != 0 ? DIAGONAL_PENALTY : 0);
                        if (value < bestScore) {
                            bestScore = value;
                            best = pos;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static double nearest(BlockPos pos, List<BlockCoord> rest) {
        double best = Double.MAX_VALUE;
        for (BlockCoord log : rest) {
            best = Math.min(best, pos.distSqr(new BlockPos(log.x(), log.y(), log.z())));
        }
        return best;
    }

    private static boolean isStandable(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        return (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced())
                && (level.getBlockState(pos.above()).isAir()
                        || level.getBlockState(pos.above()).canBeReplaced())
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getFluidState(pos).isEmpty()
                && level.getFluidState(pos.above()).isEmpty();
    }
}
