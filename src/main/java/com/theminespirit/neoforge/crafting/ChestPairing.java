package com.theminespirit.neoforge.crafting;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public final class ChestPairing {
    private ChestPairing() {}

    public static Optional<BlockState> paired(Level level, BlockPos cell, BlockState chest) {
        if (!(chest.getBlock() instanceof ChestBlock)) {
            return Optional.empty();
        }
        Direction facing = chest.getValue(ChestBlock.FACING);
        if (single(level, cell.relative(facing.getClockWise()), chest)) {
            return Optional.of(chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
        }
        if (single(level, cell.relative(facing.getCounterClockWise()), chest)) {
            return Optional.of(chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
        }
        return Optional.empty();
    }

    private static boolean single(Level level, BlockPos neighbour, BlockState chest) {
        BlockState state = level.getBlockState(neighbour);
        return state.is(chest.getBlock())
                && state.getValue(ChestBlock.FACING) == chest.getValue(ChestBlock.FACING)
                && state.getValue(ChestBlock.TYPE) == ChestType.SINGLE;
    }
}
