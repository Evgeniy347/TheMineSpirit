package com.theminespirit.neoforge;

import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockStates {
    private static final String DEFAULT_NAMESPACE = "minecraft";

    private BlockStates() {
    }

    public static Block blockFor(String blockId) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестно, что ставить");
        if (blockId.isBlank()) {
            throw new IllegalArgumentException("Id блока пуст: по пустому id блок не найти");
        }
        String qualified = blockId.indexOf(':') < 0 ? DEFAULT_NAMESPACE + ":" + blockId : blockId;
        ResourceLocation location = ResourceLocation.tryParse(qualified);
        Block block = location == null ? null : BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
        if (block == null || block == Blocks.AIR) {
            throw new IllegalArgumentException("Неизвестный блок '" + blockId + "': такого id нет в реестре блоков");
        }
        return block;
    }

    public static BlockState stateFor(String blockId) {
        return blockFor(blockId).defaultBlockState();
    }

    public static String idOf(BlockState state) {
        Objects.requireNonNull(state, "Состояние блока не может быть null: без него неизвестно, чей id называть");
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }
}
