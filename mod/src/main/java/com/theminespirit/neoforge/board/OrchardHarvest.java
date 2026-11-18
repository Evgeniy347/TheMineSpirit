package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.policy.OrchardTrees;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;

final class OrchardHarvest {
    private OrchardHarvest() {}

    static Optional<BlockCoord> grownBase(ServerLevel level, BlockCoord spot) {
        BlockPos pos = new BlockPos(spot.x(), spot.y(), spot.z());
        if (!level.isLoaded(pos) || !level.getBlockState(pos).is(BlockTags.LOGS)) {
            return Optional.empty();
        }
        return OrchardTrees.planned(level, spot) ? Optional.of(spot) : Optional.empty();
    }
}
