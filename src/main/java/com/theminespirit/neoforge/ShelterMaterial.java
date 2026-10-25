package com.theminespirit.neoforge;

import com.theminespirit.core.building.ShelterMaterials;
import java.util.LinkedHashMap;
import java.util.Map;
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

public final class ShelterMaterial {
    private ShelterMaterial() {}

    public static Optional<ShelterMaterials.Choice> choose(Container inventory, int needed, int slabNeeded) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if (!placeable(blockItem) && !id.endsWith("_slab")) {
                continue;
            }
            counts.merge(id, stack.getCount(), Integer::sum);
        }
        return ShelterMaterials.choose(counts, needed, slabNeeded);
    }

    private static boolean placeable(BlockItem blockItem) {
        BlockState state = blockItem.getBlock().defaultBlockState();
        return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                && !(blockItem.getBlock() instanceof EntityBlock)
                && !(blockItem.getBlock() instanceof FallingBlock)
                && !(blockItem.getBlock() instanceof TntBlock);
    }
}
