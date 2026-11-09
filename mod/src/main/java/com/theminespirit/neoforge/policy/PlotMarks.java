package com.theminespirit.neoforge.policy;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

final class PlotMarks {
    record Marks(boolean spiritMarked, boolean placedByPlayer, boolean onPlot) {}

    private PlotMarks() {}

    static Marks of(Level level, BlockPos pos, BlockCoord coord) {
        if (!(level instanceof ServerLevel server)) {
            return new Marks(false, false, false);
        }
        boolean spiritMarked = PlacedBlocksData.get(server).isSpiritMarked(pos);
        boolean placedByPlayer = !spiritMarked && PlacedBlocksData.get(server).isMarked(pos);
        boolean onPlot = SettlementPlotStore.get(server)
                .site()
                .map(site -> site.inside(coord.x(), coord.z()))
                .orElse(false);
        return new Marks(spiritMarked, placedByPlayer, onPlot);
    }
}
