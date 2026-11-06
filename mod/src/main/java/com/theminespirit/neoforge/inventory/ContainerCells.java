package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.inventory.ContainerCell;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.BlockStates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

public final class ContainerCells {
    private ContainerCells() {}

    public static ContainerCell at(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity entity = level.getBlockEntity(pos);
        ChestType type = chestType(state);
        return new ContainerCell(
                new BlockCoord(pos.getX(), pos.getY(), pos.getZ()),
                BlockStates.idOf(state),
                state.getBlock() instanceof ChestBlock,
                state.hasProperty(ChestBlock.TYPE),
                type == null ? null : type.getSerializedName(),
                entity == null
                        ? null
                        : BuiltInRegistries.BLOCK_ENTITY_TYPE
                                .getKey(entity.getType())
                                .toString());
    }

    public static @Nullable ChestType chestType(BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock) || !state.hasProperty(ChestBlock.TYPE)) {
            return null;
        }
        return state.getValue(ChestBlock.TYPE);
    }
}
