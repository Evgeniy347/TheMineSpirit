package com.theminespirit.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class PlaceOccupancy {
    private PlaceOccupancy() {}

    static BlockPlacer.@Nullable Begin check(SpiritEntity spirit, Level level, BlockPos target, boolean action) {
        BlockState current = level.getBlockState(target);
        if (!action && !replaceable(current)) {
            return new BlockPlacer.Refused(
                    BlockPlacer.RefuseReason.OCCUPIED,
                    "клетка " + target.toShortString() + " занята блоком " + BlockStates.idOf(current)
                            + ": ставить некуда");
        }
        if (BlockPlacer.livingEntities(level, target, spirit).isEmpty()) {
            return null;
        }
        return new BlockPlacer.Refused(
                BlockPlacer.RefuseReason.OCCUPIED,
                "клетку " + target.toShortString() + " занимает живое существо: ставить в него нельзя");
    }

    private static boolean replaceable(BlockState state) {
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty();
    }
}
