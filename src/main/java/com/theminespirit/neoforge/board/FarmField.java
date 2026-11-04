package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

final class FarmField {
    private FarmField() {}

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
