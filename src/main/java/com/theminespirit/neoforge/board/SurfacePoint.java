package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

final class SurfacePoint {
    private SurfacePoint() {}

    static BlockCoord of(ServerLevel level, BlockCoord at) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.x(), at.z());
        return new BlockCoord(at.x(), y, at.z());
    }
}
