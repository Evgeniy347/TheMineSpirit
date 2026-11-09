package com.theminespirit.neoforge.repair;

import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.neoforge.inventory.SpiritInventory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

final class RepairInventory {
    private RepairInventory() {}

    static int mostWorn(SpiritInventory inventory, IdFilter filter) {
        int slot = -1;
        int worst = 0;
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            ItemStack stack = inventory.getItem(index);
            if (stack.isEmpty() || stack.getDamageValue() <= 0 || !filter.matches(itemId(stack))) {
                continue;
            }
            if (stack.getDamageValue() > worst) {
                worst = stack.getDamageValue();
                slot = index;
            }
        }
        return slot;
    }

    static int secondDamaged(SpiritInventory inventory, IdFilter filter, Item item, int exclude) {
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            if (index == exclude) {
                continue;
            }
            ItemStack stack = inventory.getItem(index);
            if (stack.isEmpty()
                    || stack.getDamageValue() <= 0
                    || !stack.is(item)
                    || !filter.matches(itemId(stack))
                    || isEnchanted(stack)) {
                continue;
            }
            return index;
        }
        return -1;
    }

    static boolean hasEnchantedDamaged(SpiritInventory inventory, IdFilter filter) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getDamageValue() > 0 && filter.matches(itemId(stack)) && isEnchanted(stack)) {
                return true;
            }
        }
        return false;
    }

    static int countMaterial(SpiritInventory inventory, String materialItem) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && materialItem.equals(itemId(stack))) {
                total += stack.getCount();
            }
        }
        return total;
    }

    static void consumeMaterial(SpiritInventory inventory, Item item, int count) {
        int left = count;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.is(item)) {
                continue;
            }
            int taken = Math.min(left, stack.getCount());
            inventory.removeItem(slot, taken);
            left -= taken;
        }
    }

    static boolean isEnchanted(ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        return !enchantments.isEmpty();
    }

    static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
