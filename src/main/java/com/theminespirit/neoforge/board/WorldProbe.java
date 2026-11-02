package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

final class WorldProbe {
    private WorldProbe() {}

    static BuildGap.Probe of(ServerLevel level) {
        return new BuildGap.Probe() {
            @Override
            public String block(BlockCoord at) {
                return BuiltInRegistries.BLOCK
                        .getKey(level.getBlockState(new BlockPos(at.x(), at.y(), at.z()))
                                .getBlock())
                        .toString();
            }

            @Override
            public boolean passable(BlockCoord at) {
                BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
                return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
            }

            @Override
            public boolean loaded(BlockCoord at) {
                return level.isLoaded(new BlockPos(at.x(), at.y(), at.z()));
            }
        };
    }
}
