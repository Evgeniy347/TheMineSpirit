package com.theminespirit.neoforge;

import com.theminespirit.neoforge.log.SpiritWorldLog;
import java.util.Iterator;
import net.minecraft.core.registries.BuiltInRegistries;
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
            ItemStack stack = drop.getItem();
            int before = stack.getCount();
            ItemStack rest = spirit.getInventory().addItem(stack.copy());
            int taken = before - rest.getCount();
            if (taken > 0) {
                SpiritWorldLog.itemMove(
                        spirit,
                        "pickup",
                        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                        taken,
                        null);
            }
            if (rest.isEmpty()) {
                drops.remove();
            } else {
                drop.setItem(rest);
            }
        }
    }
}
