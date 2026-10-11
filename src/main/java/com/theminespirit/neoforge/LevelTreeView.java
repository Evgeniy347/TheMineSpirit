package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.TreeView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class LevelTreeView implements TreeView {
    private final Level level;
    private final Set<Block> logs;
    private long blockReads;

    public LevelTreeView(Level level, Set<Block> logs) {
        this.level = level;
        this.logs = Set.copyOf(logs);
    }

    @Override
    public boolean isLog(BlockCoord coord) {
        return logs.contains(read(coord).getBlock());
    }

    @Override
    public boolean isNaturalLeaves(BlockCoord coord) {
        BlockState state = read(coord);
        return state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT);
    }

    @Override
    public boolean isSoil(BlockCoord coord) {
        BlockState state = read(coord);
        return state.is(BlockTags.DIRT) || state.is(Blocks.MANGROVE_ROOTS);
    }

    public long blockReads() {
        return blockReads;
    }

    private BlockState read(BlockCoord coord) {
        blockReads++;
        return level.getBlockState(new BlockPos(coord.x(), coord.y(), coord.z()));
    }
}