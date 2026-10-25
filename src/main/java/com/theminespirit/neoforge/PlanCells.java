package com.theminespirit.neoforge;

import com.theminespirit.core.board.SettlementLayout;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
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
                        .filter(plot -> SettlementLayout.planCell(plot.anchor(), pos.getX(), pos.getY(), pos.getZ())
                                && !oldFill(level, plot.anchor(), pos))
                        .isPresent()
                && PlacedBlocksData.get(server).isSpiritMarked(pos);
    }

    // Земля, насыпанная поселением на уровне ног участка и выше вне спуска в шахту, — не постройка плана (ограда,
    // хижина, двор, факелы землёй не строятся), а старая подсыпка: её сохранение оставляло холмик, и выравнивание
    // ходило по кругу «срезка 2 ящиков» (сцена 56).
    private static boolean oldFill(Level level, BlockCoord anchor, BlockPos pos) {
        return pos.getY() >= anchor.y()
                && !SettlementLayout.mineColumn(anchor, pos.getX(), pos.getZ())
                && level.getBlockState(pos).is(BlockTags.DIRT);
    }
}
