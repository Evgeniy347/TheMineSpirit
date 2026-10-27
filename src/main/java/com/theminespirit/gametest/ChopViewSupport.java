package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class ChopViewSupport {
    private ChopViewSupport() {}

    static Map<BlockState, Boolean> blockClassificationTable() {
        Map<BlockState, Boolean> table = new LinkedHashMap<>();
        for (Block built : builtBlocks()) {
            table.put(built.defaultBlockState(), true);
        }
        table.put(Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), true);
        for (Block natural : naturalBlocks()) {
            table.put(natural.defaultBlockState(), false);
        }
        table.put(Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false), false);
        return table;
    }

    static List<Block> builtBlocks() {
        return List.of(
                Blocks.OAK_PLANKS,
                Blocks.OAK_DOOR,
                Blocks.GLASS,
                Blocks.GLASS_PANE,
                Blocks.TORCH,
                Blocks.WALL_TORCH,
                Blocks.OAK_STAIRS,
                Blocks.OAK_SLAB,
                Blocks.OAK_FENCE,
                Blocks.OAK_FENCE_GATE,
                Blocks.OAK_TRAPDOOR,
                Blocks.LADDER,
                Blocks.STRIPPED_OAK_LOG,
                Blocks.OAK_WOOD,
                Blocks.OAK_SIGN,
                Blocks.WHITE_BED,
                Blocks.CHEST,
                Blocks.CRAFTING_TABLE,
                Blocks.COBBLESTONE_WALL,
                Blocks.WHITE_WOOL,
                Blocks.LANTERN,
                Blocks.BOOKSHELF,
                Blocks.COBBLESTONE,
                Blocks.MOSSY_COBBLESTONE,
                Blocks.COBBLESTONE_SLAB,
                Blocks.COBBLESTONE_STAIRS,
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
                Blocks.RED_TERRACOTTA,
                Blocks.WHITE_GLAZED_TERRACOTTA,
                Blocks.RED_GLAZED_TERRACOTTA,
                Blocks.WHITE_CONCRETE,
                Blocks.RED_CONCRETE,
                Blocks.WHITE_CONCRETE_POWDER,
                Blocks.RED_CONCRETE_POWDER,
                Blocks.IRON_BARS,
                Blocks.RAIL,
                Blocks.POWERED_RAIL,
                Blocks.DETECTOR_RAIL,
                Blocks.ACTIVATOR_RAIL);
    }

    static List<Block> naturalBlocks() {
        return List.of(
                Blocks.DIRT,
                Blocks.GRASS_BLOCK,
                Blocks.STONE,
                Blocks.SAND,
                Blocks.GRAVEL,
                Blocks.WATER,
                Blocks.SHORT_GRASS,
                Blocks.FERN,
                Blocks.VINE,
                Blocks.DANDELION,
                Blocks.SNOW,
                Blocks.BROWN_MUSHROOM,
                Blocks.BEE_NEST,
                Blocks.BIRCH_LOG,
                Blocks.OAK_LOG,
                Blocks.MANGROVE_ROOTS,
                Blocks.COCOA);
    }
}
