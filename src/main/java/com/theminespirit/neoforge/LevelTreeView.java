package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.TreeView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

public final class LevelTreeView implements TreeView {
    private enum Kind {
        LOG,
        NATURAL_LEAVES,
        SOIL,
        ARTIFICIAL,
        OTHER
    }

    private static final Set<Block> BUILT_BLOCKS = Set.of(Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH,
            Blocks.SOUL_WALL_TORCH, Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH, Blocks.LANTERN,
            Blocks.SOUL_LANTERN, Blocks.LADDER, Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.BARREL,
            Blocks.BOOKSHELF);

    private static final List<TagKey<Block>> BUILT_TAGS = List.of(BlockTags.PLANKS, BlockTags.DOORS,
            BlockTags.TRAPDOORS, BlockTags.STAIRS, BlockTags.SLABS, BlockTags.WALLS, BlockTags.FENCES,
            BlockTags.FENCE_GATES, BlockTags.ALL_SIGNS, BlockTags.BEDS, BlockTags.BUTTONS,
            BlockTags.PRESSURE_PLATES, BlockTags.WOOL, BlockTags.WOOL_CARPETS, BlockTags.CANDLES,
            BlockTags.CAMPFIRES, Tags.Blocks.GLASS_BLOCKS, Tags.Blocks.GLASS_PANES, Tags.Blocks.CHESTS);

    private final Level level;
    private final Set<Block> logs;
    private BlockCoord lastCoord;
    private Kind lastKind;
    private long lastGameTime = -1;
    private long blockReads;

    public LevelTreeView(Level level, Set<Block> logs) {
        this.level = level;
        this.logs = Set.copyOf(logs);
    }

    @Override
    public boolean isLog(BlockCoord coord) {
        return kind(coord) == Kind.LOG;
    }

    @Override
    public boolean isNaturalLeaves(BlockCoord coord) {
        return kind(coord) == Kind.NATURAL_LEAVES;
    }

    @Override
    public boolean isSoil(BlockCoord coord) {
        return kind(coord) == Kind.SOIL;
    }

    @Override
    public boolean isArtificial(BlockCoord coord) {
        return kind(coord) == Kind.ARTIFICIAL;
    }

    public long blockReads() {
        return blockReads;
    }

    private Kind kind(BlockCoord coord) {
        long gameTime = level.getGameTime();
        if (gameTime != lastGameTime || !coord.equals(lastCoord)) {
            blockReads++;
            lastKind = classify(level.getBlockState(new BlockPos(coord.x(), coord.y(), coord.z())));
            lastCoord = coord;
            lastGameTime = gameTime;
        }
        return lastKind;
    }

    private Kind classify(BlockState state) {
        if (logs.contains(state.getBlock())) {
            return Kind.LOG;
        }
        if (state.getBlock() instanceof LeavesBlock) {
            return state.getValue(LeavesBlock.PERSISTENT) ? Kind.ARTIFICIAL : Kind.NATURAL_LEAVES;
        }
        if (state.is(BlockTags.LOGS)) {
            return state.is(BlockTags.OVERWORLD_NATURAL_LOGS) ? Kind.OTHER : Kind.ARTIFICIAL;
        }
        if (state.is(BlockTags.DIRT) || state.is(Blocks.MANGROVE_ROOTS)) {
            return Kind.SOIL;
        }
        return isBuilt(state) ? Kind.ARTIFICIAL : Kind.OTHER;
    }

    private static boolean isBuilt(BlockState state) {
        return BUILT_BLOCKS.contains(state.getBlock()) || BUILT_TAGS.stream().anyMatch(state::is);
    }
}
