package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.CarriedItems;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

final class SpiritItems {
    private SpiritItems() {}

    static List<String> ids(SpiritEntity spirit) {
        List<String> ids = new ArrayList<>();
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
        ItemStack hand = spirit.getMainHandItem();
        return CarriedItems.of(
                ids,
                spirit.mainHandOwned() && !hand.isEmpty()
                        ? Optional.of(
                                BuiltInRegistries.ITEM.getKey(hand.getItem()).toString())
                        : Optional.empty());
    }
}
