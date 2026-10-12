package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.inventory.Slice;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.UnloadParams;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class UnloadScript extends ContainerTask {
    private final UnloadParams params;

    public UnloadScript(SpiritEntity spirit, UnloadParams params) {
        super(spirit, params.container(), params.radius());
        this.params = params;
    }

    @Override
    protected boolean skipAsDone() {
        return !params.force() && occupiedSlots() < CatalogLimits.UNLOAD_TRIGGER_SLOTS;
    }

    @Override
    protected TransferRunner beginTransfer(Container container) {
        List<Slice> surplus = InventoryTables.kit()
                .surplus(new StackMapper().map(spirit.getInventory()), InventoryTables.values());
        if (surplus.isEmpty()) {
            return null;
        }
        return TransferRunner.slices(spirit.getInventory(), container, surplus);
    }

    @Override
    protected ScriptStatus finish(int moved, TransferRunner.Shortage shortage) {
        return switch (shortage) {
            case NONE, SOURCE_LACK -> ScriptStatus.SUCCEEDED;
            case DEST_FULL -> failed("контейнер полон: разгружено " + moved
                    + " предметов, остаток излишка остался у духа");
        };
    }

    private int occupiedSlots() {
        int occupied = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                occupied++;
            }
        }
        return occupied;
    }
}
