package com.theminespirit.neoforge;

import com.theminespirit.core.board.SettlementLayout;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Блок постройки плана поселения, который расчистка не трогает: клетка плана и поставлен поселением. */
final class PlanCells {
    private PlanCells() {}

    static boolean keep(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        return SettlementPlotStore.get(server)
                        .plot()
                        .filter(plot -> SettlementLayout.planCell(plot.anchor(), pos.getX(), pos.getY(), pos.getZ()))
                        .isPresent()
                && PlacedBlocksData.get(server).isSpiritMarked(pos);
    }
}
