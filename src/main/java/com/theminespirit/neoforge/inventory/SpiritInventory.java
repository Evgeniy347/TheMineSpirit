package com.theminespirit.neoforge.inventory;

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
}
