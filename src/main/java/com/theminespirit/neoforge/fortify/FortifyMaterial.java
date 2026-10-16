package com.theminespirit.neoforge.fortify;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;

final class FortifyMaterial {
    private FortifyMaterial() {
    }

    static Optional<String> placeableBlockId(Container inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (placeable(stack)) {
                return Optional.of(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
        return Optional.empty();
    }

    private static boolean placeable(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                && !(blockItem.getBlock() instanceof EntityBlock)
                && !(blockItem.getBlock() instanceof FallingBlock)
                && !(blockItem.getBlock() instanceof TntBlock);
    }
}