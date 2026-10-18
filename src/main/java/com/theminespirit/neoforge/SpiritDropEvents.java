package com.theminespirit.neoforge;

import java.util.Iterator;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Дроп блока, сломанного духом, сразу в его инвентарь (решение владельца 18.10, «чит» подбора): событие
 * {@link BlockDropsEvent} приходит только за блок, который дух ломал сам как цель задачи (руда жилы, бревно,
 * урожай, очищаемая клетка), поэтому чужое не трогается. Не влезло — остаётся предметом на месте блока.
 * Опыт блока обрабатывает {@code SpiritExperienceEvents} в том же событии.
 */
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
