package com.theminespirit.gametest;

import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

final class SpiritHoldings {
    private SpiritHoldings() {}

    static String woodHeld(SpiritEntity spirit) {
        int logs = 0;
        int planks = 0;
        int sticks = 0;
        int stone = 0;
        int coal = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            logs += id.endsWith("_log") ? stack.getCount() : 0;
            planks += id.endsWith("_planks") ? stack.getCount() : 0;
            sticks += "stick".equals(id) ? stack.getCount() : 0;
            stone += "cobblestone".equals(id) ? stack.getCount() : 0;
            coal += id.endsWith("coal") ? stack.getCount() : 0;
        }
        return "log" + logs + "/pl" + planks + "/st" + sticks + "/cb" + stone + "/c" + coal;
    }

    static int countItems(SpiritEntity spirit) {
        int count = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            count += spirit.getInventory().getItem(slot).getCount();
        }
        return count;
    }
}
