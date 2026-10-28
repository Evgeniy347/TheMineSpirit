package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.ChopStands;
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
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockCoord stand : ChopStands.around(target, feet -> isStandable(level, toPos(feet)))) {
            BlockPos pos = toPos(stand);
            if (pos.equals(used)) {
                continue;
            }
            boolean diagonal = pos.getX() != target.base().x()
                    && pos.getZ() != target.base().z();
            double value = score.applyAsDouble(pos) + (diagonal ? DIAGONAL_PENALTY : 0);
            if (value < bestScore) {
                bestScore = value;
                best = pos;
            }
        }
        return best;
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
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
