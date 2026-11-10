package com.theminespirit.neoforge.board;

import com.theminespirit.core.movement.PerchLevel;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

final class ScaffoldPerch {
    private static final int RADIUS = 3;

    private ScaffoldPerch() {}

    static boolean on(SpiritEntity spirit) {
        if (!(spirit.level() instanceof ServerLevel server)) {
            return false;
        }
        BlockPos feet = spirit.blockPosition();
        BlockPos below = feet.below();
        if (server.getBlockState(below).isAir() || !PlacedBlocksData.get(server).isTemporary(below)) {
            return false;
        }
        List<Integer> heights = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx != 0 || dz != 0) {
                    heights.add(server.getHeight(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX() + dx, feet.getZ() + dz));
                }
            }
        }
        return PerchLevel.perched(DigOutScript.open(server, feet), feet.getY(), heights);
    }
}
