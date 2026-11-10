package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.ChopStands;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class ChopStand {
    private static final double DIAGONAL_PENALTY = 10_000;
    private static final double CLIMB_BLOCK_PENALTY = 1_000_000;

    record Placement(BlockPos feet, @Nullable BlockCoord climbFrom) {}

    private ChopStand() {}

    static @Nullable Placement first(SpiritEntity spirit, Tree target) {
        Vec3 from = spirit.position();
        return best(spirit.level(), target, List.of(), pos -> from.distanceToSqr(Vec3.atBottomCenterOf(pos)));
    }

    static @Nullable Placement next(SpiritEntity spirit, Tree target, List<BlockCoord> rest, List<BlockPos> used) {
        return best(spirit.level(), target, used, pos -> nearest(pos, rest));
    }

    private static @Nullable Placement best(
            Level level, Tree target, List<BlockPos> used, ToDoubleFunction<BlockPos> score) {
        Placement best = null;
        double bestScore = Double.MAX_VALUE;
        ChopStands.View view = new LevelView(level);
        for (ChopStands.Stand stand : ChopStands.standPoints(target, view)) {
            BlockPos pos = toPos(stand.feet());
            if (used.contains(pos)) {
                continue;
            }
            boolean diagonal = pos.getX() != target.base().x()
                    && pos.getZ() != target.base().z();
            double value = score.applyAsDouble(pos) + (diagonal ? DIAGONAL_PENALTY : 0);
            BlockCoord base = stand.climbFrom();
            if (base != null) {
                value += CLIMB_BLOCK_PENALTY * (stand.feet().y() - base.y());
            }
            if (value < bestScore) {
                bestScore = value;
                best = new Placement(pos, base);
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
        return !level.getBlockState(pos).is(BlockTags.LEAVES)
                && !level.getBlockState(pos.above()).is(BlockTags.LEAVES)
                && !level.getBlockState(below).is(BlockTags.LEAVES)
                && (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced())
                && (level.getBlockState(pos.above()).isAir()
                        || level.getBlockState(pos.above()).canBeReplaced())
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getFluidState(pos).isEmpty()
                && level.getFluidState(pos.above()).isEmpty();
    }

    private static boolean isFree(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.is(BlockTags.LEAVES)
                && (state.isAir() || state.canBeReplaced())
                && level.getFluidState(pos).isEmpty();
    }

    private record LevelView(Level level) implements ChopStands.View {
        @Override
        public boolean standable(BlockCoord feet) {
            return isStandable(level, toPos(feet));
        }

        @Override
        public boolean free(BlockCoord cell) {
            return isFree(level, toPos(cell));
        }
    }
}
