package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;

final class SchematicWild {
    private SchematicWild() {}

    static boolean log(Level level, Set<BlockCoord> schemeCells, BlockCoord cell) {
        BlockPos pos = SchematicSite.toPos(cell);
        boolean placed = level instanceof ServerLevel server
                && PlacedBlocksData.get(server).isMarked(pos);
        return !schemeCells.contains(cell)
                && !placed
                && level.getBlockState(pos).is(BlockTags.LOGS);
    }
}
