package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;

final class FarmField {
    private static final int RADIUS = 16;
    private static final int BELOW = 2;
    private static final int ABOVE = 2;

    private FarmField() {}

    static boolean exists(ServerLevel level, BlockCoord home) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dy = -BELOW; dy <= ABOVE; dy++) {
                    BlockPos at = new BlockPos(home.x() + dx, home.y() + dy, home.z() + dz);
                    if (!level.isLoaded(at)) {
                        continue;
                    }
                    var state = level.getBlockState(at);
                    if (state.getBlock() instanceof FarmBlock || state.getBlock() instanceof CropBlock) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean chestNear(ServerLevel level, BlockCoord home) {
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos at = new BlockPos(home.x() + dx, home.y() + dy, home.z() + dz);
                    if (level.isLoaded(at) && level.getBlockState(at).is(Blocks.CHEST)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
