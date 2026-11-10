package com.theminespirit.neoforge;

import com.theminespirit.core.movement.SealedSpace;
import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
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
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cell.getX(), cell.getZ()) <= cell.getY();
    }
}
