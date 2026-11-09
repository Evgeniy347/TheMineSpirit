package com.theminespirit.neoforge.policy;

import com.theminespirit.core.policy.BlockProtection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

final class ProtectionNeighbours {
    private static final String NATURAL_STAND_IN = "minecraft:stone";

    List<String> ids(Level level, BlockPos pos) {
        List<String> ids = new ArrayList<>(BlockProtection.NEIGHBOUR_COUNT);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        BlockPos near = pos.offset(dx, dy, dz);
                        boolean spirits = level instanceof ServerLevel server
                                && PlacedBlocksData.get(server).isSpiritMarked(near);
                        ids.add(spirits ? NATURAL_STAND_IN : ProtectionGate.blockId(level.getBlockState(near)));
                    }
                }
            }
        }
        return ids;
    }
}
