package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.PlotGround;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class StrayBlocks {
    private StrayBlocks() {}

    public static List<BlockCoord> find(ServerLevel level, StageSite site, ColumnMask region) {
        PlacedBlocksData placed = PlacedBlocksData.get(level);
        List<BlockCoord> stray = new ArrayList<>();
        int chunkX1 = region.minX() >> 4;
        int chunkZ1 = region.minZ() >> 4;
        int chunkX2 = (region.minX() + region.width() - 1) >> 4;
        int chunkZ2 = (region.minZ() + region.depth() - 1) >> 4;
        for (int cx = chunkX1; cx <= chunkX2; cx++) {
            for (int cz = chunkZ1; cz <= chunkZ2; cz++) {
                for (BlockCoord cell : placed.spiritMarkedIn(cx, cz)) {
                    if (region.contains(cell.x(), cell.z()) && stray(level, site, cell)) {
                        stray.add(cell);
                    }
                }
            }
        }
        return List.copyOf(stray);
    }

    private static boolean stray(ServerLevel level, StageSite site, BlockCoord cell) {
        if (site.planned(cell.x(), cell.y(), cell.z())) {
            return false;
        }
        BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (PlotGround.natural(state)
                && !level.getBlockState(pos.below())
                        .getCollisionShape(level, pos.below())
                        .isEmpty()) {
            return false;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return !(entity instanceof Container container) || container.isEmpty();
    }
}
