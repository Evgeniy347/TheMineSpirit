package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanTuning;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

final class BlockLocateSource implements LocateSource {
    private final BlockLocator.Search search;

    BlockLocateSource(Level level, BlockPos center, int radius, Set<Block> blocks) {
        this.search = BlockLocator.search(level, center, radius, blocks);
    }

    @Override
    public List<BlockCoord> advance() {
        return search.step(ScanTuning.SLICES_PER_TICK).stream()
                .map(pos -> new BlockCoord(pos.getX(), pos.getY(), pos.getZ()))
                .toList();
    }

    @Override
    public boolean done() {
        return search.done();
    }

    @Override
    public int examinedPositions() {
        return search.examinedPositions();
    }
}
