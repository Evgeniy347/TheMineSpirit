package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.inventory.ChestSorter;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.inventory.ValueTable;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class ContainerSort {
    public static final int MAX_SLOTS = 90;

    private ContainerSort() {
    }

    public static void apply(Container container, ValueTable values) {
        if (container.getContainerSize() > MAX_SLOTS) {
            throw new IllegalArgumentException("Контейнер на " + container.getContainerSize()
                    + " слотов больше " + MAX_SLOTS + ": раскладка сортёра рассчитана на сундуки до двойного");
        }
        StackMapper mapper = new StackMapper();
        List<Stack> layout = ChestSorter.sort(mapper.map(container), values);
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (slot < layout.size()) {
                Stack stack = layout.get(slot);
                container.setItem(slot, mapper.sampleFor(stack.variant()).copyWithCount(stack.count()));
            } else {
                container.setItem(slot, ItemStack.EMPTY);
            }
        }
        container.setChanged();
    }
}
