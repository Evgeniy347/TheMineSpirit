package com.theminespirit.neoforge.crafting;

import com.theminespirit.core.crafting.CampfireMaterials;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class CampfireKit {
    private CampfireKit() {
    }

    static boolean ensure(SpiritEntity spirit) {
        if (spirit.getInventory().countItem(StationType.CAMPFIRE.item()) > 0) {
            return true;
        }
        Map<String, Integer> counts = CraftInventory.counts(spirit.getInventory());
        Optional<CampfireMaterials.Cost> cost = CampfireMaterials.cost(counts);
        if (cost.isEmpty() || !resolvable(spirit, cost.get())) {
            return false;
        }
        cost.get().consumed().forEach((id, count) ->
                ItemIds.item(id).ifPresent(item -> spirit.getInventory().removeItemType(item, count)));
        ItemStack leftover = spirit.getInventory().addItem(new ItemStack(StationType.CAMPFIRE.item()));
        if (!leftover.isEmpty()) {
            cost.get().consumed().forEach((id, count) -> ItemIds.item(id)
                    .ifPresent(item -> spirit.getInventory().addItem(new ItemStack(item, count))));
            return false;
        }
        return true;
    }

    private static boolean resolvable(SpiritEntity spirit, CampfireMaterials.Cost cost) {
        for (Map.Entry<String, Integer> entry : cost.consumed().entrySet()) {
            Optional<Item> item = ItemIds.item(entry.getKey());
            if (item.isEmpty() || spirit.getInventory().countItem(item.get()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }
}
