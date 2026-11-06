package com.theminespirit.neoforge;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class PlotGround {
    private PlotGround() {}

    public static boolean natural(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.CLAY);
    }
}
