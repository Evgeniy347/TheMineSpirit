package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.ToolFate;
import com.theminespirit.core.board.ToolsConfig;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.repair.RepairCalculator;
import com.theminespirit.core.repair.RepairData;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

final class ToolWear {
    private ToolWear() {}

    static boolean repairable(ItemStack stack) {
        if (stack.isEmpty() || stack.getDamageValue() <= 0 || stack.getMaxDamage() <= 0) {
            return false;
        }
        int maxDurability = stack.getMaxDamage();
        int durability = maxDurability - stack.getDamageValue();
        int percent = RepairData.loadDefault().maxDurabilityPercent();
        if (!RepairCalculator.damagedEnoughToRepair(durability, maxDurability, percent)) {
            return false;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        ToolsConfig tools = DataTables.INSTANCE.board().current().tools();
        return tools.fate(itemId, true) == ToolFate.REPAIR;
    }

    static Set<String> worn(SpiritEntity spirit) {
        Set<String> worn = new LinkedHashSet<>();
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            add(worn, spirit.getInventory().getItem(slot));
        }
        if (spirit.mainHandOwned()) {
            add(worn, spirit.getMainHandItem());
        }
        return Set.copyOf(worn);
    }

    private static void add(Set<String> worn, ItemStack stack) {
        if (repairable(stack)) {
            worn.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
    }
}
