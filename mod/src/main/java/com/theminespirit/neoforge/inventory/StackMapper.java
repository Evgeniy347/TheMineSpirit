package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.inventory.Stack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class StackMapper {
    private final List<ItemStack> samples = new ArrayList<>();

    public List<Stack> map(Container container) {
        List<Stack> stacks = new ArrayList<>(container.getContainerSize());
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            stacks.add(of(container.getItem(slot)));
        }
        return stacks;
    }

    public Stack of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        int variant = variantOf(stack);
        return new Stack(itemId(stack), variant, stack.getCount(), stack.getMaxStackSize());
    }

    public ItemStack sampleFor(int variant) {
        return samples.get(variant);
    }

    public static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private int variantOf(ItemStack stack) {
        for (int variant = 0; variant < samples.size(); variant++) {
            if (ItemStack.isSameItemSameComponents(samples.get(variant), stack)) {
                return variant;
            }
        }
        samples.add(stack.copyWithCount(1));
        return samples.size() - 1;
    }
}
