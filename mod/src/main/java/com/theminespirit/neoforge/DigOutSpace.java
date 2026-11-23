package com.theminespirit.neoforge;

import com.theminespirit.core.movement.SealedSpace;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.board.TreeSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

final class DigOutSpace {
    private DigOutSpace() {}

    static boolean sealed(Level level, BlockPos feet) {
        return SealedSpace.sealed(new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), new DigOutSealedView(level));
    }

    static boolean embedded(SpiritEntity spirit) {
        return !EmbeddedSpace.blocking(spirit).isEmpty();
    }

    static boolean open(Level level, BlockPos cell) {
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cell.getX(), cell.getZ());
        BlockState under = level.getBlockState(cell.below());
        boolean woodBelow = under.is(BlockTags.LOGS) || under.is(BlockTags.LEAVES);
        return TreeSurface.sky(height, cell.getY(), woodBelow);
    }
}
