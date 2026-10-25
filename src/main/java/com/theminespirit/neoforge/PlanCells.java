package com.theminespirit.neoforge;

import com.theminespirit.core.board.SettlementLayout;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

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

    private static boolean oldFill(Level level, BlockCoord anchor, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (SettlementLayout.mineColumn(anchor, pos.getX(), pos.getZ())) {
            String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            return !SchematicBlocks.isFloorClass(id) && !state.is(Blocks.TORCH) && !state.is(Blocks.WALL_TORCH);
        }
        return pos.getY() >= anchor.y() && state.is(BlockTags.DIRT);
    }
}
