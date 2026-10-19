package com.theminespirit.neoforge;

import java.util.Objects;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class BlockItems {
    private BlockItems() {
    }

    public static Item itemFor(String blockId) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестно, какой предмет списывать");
        Block block = BlockStates.blockFor(blockId);
        Item item = block.asItem();
        if (!(item instanceof BlockItem)) {
            throw new IllegalArgumentException("Блок '" + blockId + "' не имеет предмета для постановки: без BlockItem его не удержать в инвентаре");
        }
        return item;
    }

    /** Есть ли у блока предмет: воду и подобные блоки в инвентарь не положить, их ставят напрямую. */
    public static boolean hasBlockItem(String blockId) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестно, что ставить");
        return BlockStates.blockFor(blockId).asItem() instanceof BlockItem;
    }

    public static int countIn(Container inventory, String blockId) {
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: без него нечего считать");
        Item wanted;
        try {
            wanted = itemFor(blockId);
        } catch (IllegalArgumentException missing) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(wanted)) {
                total += stack.getCount();
            }
        }
        return total;
    }

}
