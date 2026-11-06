package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

final class HomeBlocks {
    static final int STATION_DY = 4;

    private HomeBlocks() {}

    static boolean near(ServerLevel level, BlockCoord center, int radius, Predicate<BlockState> match) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                for (int dy = -STATION_DY; dy <= STATION_DY; dy++) {
                    BlockPos check = new BlockPos(center.x() + dx, center.y() + dy, center.z() + dz);
                    if (level.isLoaded(check) && match.test(level.getBlockState(check))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
