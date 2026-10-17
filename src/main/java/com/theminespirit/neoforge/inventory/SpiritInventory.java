package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.inventory.InventorySpace;
import com.theminespirit.core.inventory.Stack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

public final class SpiritInventory extends SimpleContainer {
    private long changeCount;

    public SpiritInventory(int size) {
        super(size);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, stack);
        changeCount++;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        changeCount++;
    }

    public long changeCount() {
        return changeCount;
    }

    public boolean canAddAll(List<ItemStack> items) {
        StackMapper mapper = new StackMapper();
        List<Stack> inventory = mapper.map(this);
        List<Stack> incoming = new ArrayList<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                incoming.add(mapper.of(stack));
            }
        }
        return InventorySpace.holds(inventory, incoming);
    }
}
