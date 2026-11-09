package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.OpenBlock;
import com.theminespirit.core.observation.OpenBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class ScanOpenBlocks {
    private final Level level;
    private final BlockCoord center;
    private final Set<Block> filteredBlocks;
    private final List<OpenBlock> blocks = new ArrayList<>();
    private final int limit;

    ScanOpenBlocks(Level level, BlockCoord center, Set<Block> filteredBlocks, int limit) {
        this.level = level;
        this.center = center;
        this.filteredBlocks = filteredBlocks;
        this.limit = limit;
    }

    void collect(BlockState state, int x, int y, int z) {
        if (limit == 0 || state.isAir()) {
            return;
        }
        String item = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (filteredBlocks != null && !filteredBlocks.contains(state.getBlock())) {
            return;
        }
        if (!OpenBlocks.isImportant(item)) {
            return;
        }
        BlockPos pos = new BlockPos(x, y, z);
        if (!hasAirNeighbor(pos)) {
            return;
        }
        double dx = x - center.x();
        double dy = y - center.y();
        double dz = z - center.z();
        double distance = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz) * 10.0) / 10.0;
        OpenBlocks.keepNearest(blocks, new OpenBlock(item, toCore(pos), distance), limit);
    }

    List<OpenBlock> result() {
        return blocks;
    }

    private boolean hasAirNeighbor(BlockPos pos) {
        return level.getBlockState(pos.offset(1, 0, 0)).isAir()
                || level.getBlockState(pos.offset(-1, 0, 0)).isAir()
                || level.getBlockState(pos.offset(0, 1, 0)).isAir()
                || level.getBlockState(pos.offset(0, -1, 0)).isAir()
                || level.getBlockState(pos.offset(0, 0, 1)).isAir()
                || level.getBlockState(pos.offset(0, 0, -1)).isAir();
    }

    private static BlockCoord toCore(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
