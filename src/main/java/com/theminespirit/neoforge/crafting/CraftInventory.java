package com.theminespirit.neoforge.crafting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class CraftInventory {
    private CraftInventory() {
    }

    static Map<String, Integer> counts(Container container) {
        Map<String, Integer> counts = new HashMap<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                counts.merge(ItemIds.of(stack.getItem()), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    static SimpleContainer copyOf(Container container) {
        SimpleContainer copy = new SimpleContainer(container.getContainerSize());
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            copy.setItem(slot, container.getItem(slot).copy());
        }
        return copy;
    }

    static boolean apply(SimpleContainer container, List<Item> consumed, List<ItemStack> produced) {
        Map<Item, Integer> needed = new HashMap<>();
        consumed.forEach(item -> needed.merge(item, 1, Integer::sum));
        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            if (container.countItem(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        consumed.forEach(item -> container.removeItemType(item, 1));
        for (ItemStack stack : produced) {
            if (!container.addItem(stack.copy()).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
