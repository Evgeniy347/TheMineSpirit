package com.theminespirit.neoforge.blocks;

import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class UsableBlocks {
    public enum Kind {
        PRESSABLE,
        OPENABLE,
        CONTAINER,
        NONE
    }

    private UsableBlocks() {}

    public static Kind kind(BlockState state) {
        if (state.getBlock() instanceof LeverBlock || state.getBlock() instanceof ButtonBlock) {
            return Kind.PRESSABLE;
        }
        if (state.hasProperty(BlockStateProperties.OPEN)) {
            return Kind.OPENABLE;
        }
        if (state.getBlock() instanceof ChestBlock) {
            return Kind.CONTAINER;
        }
        return Kind.NONE;
    }

    public static boolean pressable(BlockState state) {
        Kind kind = kind(state);
        return kind == Kind.PRESSABLE || kind == Kind.OPENABLE;
    }
}
