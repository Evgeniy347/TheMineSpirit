package com.theminespirit.neoforge.policy;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class OrchardTrees {
    private static final int PLANT_BELOW = 1;
    private static final int PLANT_ABOVE = 2;

    private OrchardTrees() {}

    public static boolean treePart(BlockState state) {
        return state.is(BlockTags.SAPLINGS) || state.is(BlockTags.LOGS);
    }

    public static boolean planned(Level level, BlockCoord cell) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        return SettlementPlotStore.liveSite(server)
                .filter(site -> site.orchardTree(cell.x(), cell.y(), cell.z()))
                .filter(site -> planted(server, site, cell))
                .isPresent();
    }

    private static boolean planted(ServerLevel level, StageSite site, BlockCoord cell) {
        PlacedBlocksData placed = PlacedBlocksData.get(level);
        int anchor = site.anchor().y();
        for (int y = anchor - PLANT_BELOW; y <= anchor + PLANT_ABOVE; y++) {
            if (placed.isSpiritMarked(new BlockPos(cell.x(), y, cell.z()))) {
                return true;
            }
        }
        return false;
    }
}
