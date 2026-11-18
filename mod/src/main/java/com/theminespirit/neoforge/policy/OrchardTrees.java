package com.theminespirit.neoforge.policy;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class OrchardTrees {

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
                .isPresent();
    }
}
