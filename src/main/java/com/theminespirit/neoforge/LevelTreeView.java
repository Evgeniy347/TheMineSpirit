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
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.Tags;

public final class LevelTreeView implements TreeView {
    private enum Kind {
        LOG,
        NATURAL_LEAVES,
        SOIL,
        ARTIFICIAL,
        EMPTY,
        OTHER,
        UNKNOWN
    }

    private static final Set<Block> BUILT_BLOCKS = Set.of(
            Blocks.TORCH,
            Blocks.WALL_TORCH,
            Blocks.SOUL_TORCH,
            Blocks.SOUL_WALL_TORCH,
            Blocks.REDSTONE_TORCH,
            Blocks.REDSTONE_WALL_TORCH,
            Blocks.LANTERN,
            Blocks.SOUL_LANTERN,
            Blocks.LADDER,
            Blocks.CRAFTING_TABLE,
            Blocks.FURNACE,
            Blocks.BARREL,
            Blocks.BOOKSHELF);

    private static final List<Block> MATERIAL_BLOCKS = List.of(
            Blocks.COBBLESTONE,
            Blocks.MOSSY_COBBLESTONE,
            Blocks.COBBLESTONE_SLAB,
            Blocks.COBBLESTONE_STAIRS,
            Blocks.COBBLESTONE_WALL,
            Blocks.MOSSY_COBBLESTONE_SLAB,
            Blocks.MOSSY_COBBLESTONE_STAIRS,
            Blocks.MOSSY_COBBLESTONE_WALL,
            Blocks.STONE_BRICKS,
            Blocks.MOSSY_STONE_BRICKS,
            Blocks.CRACKED_STONE_BRICKS,
            Blocks.CHISELED_STONE_BRICKS,
            Blocks.STONE_BRICK_SLAB,
            Blocks.STONE_BRICK_STAIRS,
            Blocks.STONE_BRICK_WALL,
            Blocks.MOSSY_STONE_BRICK_SLAB,
            Blocks.MOSSY_STONE_BRICK_STAIRS,
            Blocks.MOSSY_STONE_BRICK_WALL,
            Blocks.BRICKS,
            Blocks.BRICK_SLAB,
            Blocks.BRICK_STAIRS,
            Blocks.BRICK_WALL,
            Blocks.TERRACOTTA,
            Blocks.WHITE_TERRACOTTA,
            Blocks.LIGHT_GRAY_TERRACOTTA,
            Blocks.GRAY_TERRACOTTA,
            Blocks.BLACK_TERRACOTTA,
            Blocks.BROWN_TERRACOTTA,
            Blocks.RED_TERRACOTTA,
            Blocks.ORANGE_TERRACOTTA,
            Blocks.YELLOW_TERRACOTTA,
            Blocks.LIME_TERRACOTTA,
            Blocks.GREEN_TERRACOTTA,
            Blocks.CYAN_TERRACOTTA,
            Blocks.LIGHT_BLUE_TERRACOTTA,
            Blocks.BLUE_TERRACOTTA,
            Blocks.PURPLE_TERRACOTTA,
            Blocks.MAGENTA_TERRACOTTA,
            Blocks.PINK_TERRACOTTA,
            Blocks.WHITE_GLAZED_TERRACOTTA,
            Blocks.LIGHT_GRAY_GLAZED_TERRACOTTA,
            Blocks.GRAY_GLAZED_TERRACOTTA,
            Blocks.BLACK_GLAZED_TERRACOTTA,
            Blocks.BROWN_GLAZED_TERRACOTTA,
            Blocks.RED_GLAZED_TERRACOTTA,
            Blocks.ORANGE_GLAZED_TERRACOTTA,
            Blocks.YELLOW_GLAZED_TERRACOTTA,
            Blocks.LIME_GLAZED_TERRACOTTA,
            Blocks.GREEN_GLAZED_TERRACOTTA,
            Blocks.CYAN_GLAZED_TERRACOTTA,
            Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA,
            Blocks.BLUE_GLAZED_TERRACOTTA,
            Blocks.PURPLE_GLAZED_TERRACOTTA,
            Blocks.MAGENTA_GLAZED_TERRACOTTA,
            Blocks.PINK_GLAZED_TERRACOTTA,
            Blocks.WHITE_CONCRETE,
            Blocks.LIGHT_GRAY_CONCRETE,
            Blocks.GRAY_CONCRETE,
            Blocks.BLACK_CONCRETE,
            Blocks.BROWN_CONCRETE,
            Blocks.RED_CONCRETE,
            Blocks.ORANGE_CONCRETE,
            Blocks.YELLOW_CONCRETE,
            Blocks.LIME_CONCRETE,
            Blocks.GREEN_CONCRETE,
            Blocks.CYAN_CONCRETE,
            Blocks.LIGHT_BLUE_CONCRETE,
            Blocks.BLUE_CONCRETE,
            Blocks.PURPLE_CONCRETE,
            Blocks.MAGENTA_CONCRETE,
            Blocks.PINK_CONCRETE,
            Blocks.WHITE_CONCRETE_POWDER,
            Blocks.LIGHT_GRAY_CONCRETE_POWDER,
            Blocks.GRAY_CONCRETE_POWDER,
            Blocks.BLACK_CONCRETE_POWDER,
            Blocks.BROWN_CONCRETE_POWDER,
            Blocks.RED_CONCRETE_POWDER,
            Blocks.ORANGE_CONCRETE_POWDER,
            Blocks.YELLOW_CONCRETE_POWDER,
            Blocks.LIME_CONCRETE_POWDER,
            Blocks.GREEN_CONCRETE_POWDER,
            Blocks.CYAN_CONCRETE_POWDER,
            Blocks.LIGHT_BLUE_CONCRETE_POWDER,
            Blocks.BLUE_CONCRETE_POWDER,
            Blocks.PURPLE_CONCRETE_POWDER,
            Blocks.MAGENTA_CONCRETE_POWDER,
            Blocks.PINK_CONCRETE_POWDER,
            Blocks.IRON_BARS,
            Blocks.RAIL,
            Blocks.POWERED_RAIL,
            Blocks.DETECTOR_RAIL,
            Blocks.ACTIVATOR_RAIL);

    private static final Set<Block> BUILT_MATERIALS = Set.copyOf(MATERIAL_BLOCKS);

    private static final List<TagKey<Block>> BUILT_TAGS = List.of(
            BlockTags.PLANKS,
            BlockTags.DOORS,
            BlockTags.TRAPDOORS,
            BlockTags.STAIRS,
            BlockTags.SLABS,
            BlockTags.WALLS,
            BlockTags.FENCES,
            BlockTags.FENCE_GATES,
            BlockTags.ALL_SIGNS,
            BlockTags.BEDS,
            BlockTags.BUTTONS,
            BlockTags.PRESSURE_PLATES,
            BlockTags.WOOL,
            BlockTags.WOOL_CARPETS,
            BlockTags.CANDLES,
            BlockTags.CAMPFIRES,
            Tags.Blocks.GLASS_BLOCKS,
            Tags.Blocks.GLASS_PANES,
            Tags.Blocks.CHESTS);

    private final Level level;
    private final Set<Block> logs;
    private BlockCoord lastCoord;
    private Kind lastKind;
    private long lastGameTime = -1;

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

    @Override
    public boolean isEmpty(BlockCoord coord) {
        return kind(coord) == Kind.EMPTY;
    }

    private Kind kind(BlockCoord coord) {
        long gameTime = level.getGameTime();
        if (gameTime != lastGameTime || !coord.equals(lastCoord)) {
            lastKind = classify(coord);
            lastCoord = coord;
            lastGameTime = gameTime;
        }
        return lastKind;
    }

    private Kind classify(BlockCoord coord) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(coord.x() >> 4, coord.z() >> 4);
        if (chunk == null) {
            return Kind.UNKNOWN;
        }
        return classify(chunk.getBlockState(new BlockPos(coord.x(), coord.y(), coord.z())));
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
        if (isBuilt(state)) {
            return Kind.ARTIFICIAL;
        }
        return state.isAir() || state.canBeReplaced() ? Kind.EMPTY : Kind.OTHER;
    }

    private static boolean isBuilt(BlockState state) {
        return BUILT_BLOCKS.contains(state.getBlock())
                || BUILT_MATERIALS.contains(state.getBlock())
                || BUILT_TAGS.stream().anyMatch(state::is);
    }
}
