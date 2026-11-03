package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.OrchardPlans;
import com.theminespirit.core.board.WoodRules;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.policy.OrchardTrees;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;

final class OrchardHarvest {
    private static final int BASE_BELOW = 1;
    private static final int BASE_ABOVE = 2;

    private OrchardHarvest() {}

    static Optional<BlockCoord> matureBase(ServerLevel level, StageSite stage, BlockCoord spot) {
        Optional<BlockCoord> base = base(level, stage, spot);
        if (base.isEmpty()) {
            return Optional.empty();
        }
        WoodRules rules = DataTables.INSTANCE.wood().current();
        int logs = trunk(level, base.orElseThrow());
        return OrchardPlans.mature(logs, rules) ? base : Optional.empty();
    }

    private static Optional<BlockCoord> base(ServerLevel level, StageSite stage, BlockCoord spot) {
        int anchor = stage.anchor().y();
        for (int y = anchor - BASE_BELOW; y <= anchor + BASE_ABOVE; y++) {
            BlockCoord cell = new BlockCoord(spot.x(), y, spot.z());
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (level.isLoaded(pos) && level.getBlockState(pos).is(BlockTags.LOGS)) {
                return OrchardTrees.planned(level, cell) ? Optional.of(cell) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static int trunk(ServerLevel level, BlockCoord base) {
        int logs = 0;
        BlockPos pos = new BlockPos(base.x(), base.y(), base.z());
        while (logs <= StageSite.ORCHARD_TREE_HEIGHT && level.getBlockState(pos).is(BlockTags.LOGS)) {
            logs++;
            pos = pos.above();
        }
        return logs;
    }
}
