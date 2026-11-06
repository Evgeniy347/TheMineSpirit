package com.theminespirit.neoforge;

import java.util.Iterator;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

public final class SpiritDropEvents {
    public SpiritDropEvents(IEventBus bus) {
        bus.addListener(this::onBlockDrops);
    }

    private void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof SpiritEntity spirit)) {
            return;
        }
        Iterator<ItemEntity> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemEntity drop = drops.next();
            ItemStack rest = spirit.getInventory().addItem(drop.getItem().copy());
            if (rest.isEmpty()) {
                drops.remove();
            } else {
                drop.setItem(rest);
            }
        }
    }
}
