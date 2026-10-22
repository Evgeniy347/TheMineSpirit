package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.inventory.ValueTable;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.script.EquipParams;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public final class EquipScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final EquipParams params;
    private ScriptStatus status = ScriptStatus.RUNNING;
    private String reason;

    public EquipScript(SpiritEntity spirit, EquipParams params) {
        this.spirit = spirit;
        this.params = params;
    }

    @Override
    public ScriptStatus tick() {
        if (status != ScriptStatus.RUNNING) {
            return status;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время надевания");
        }
        IdFilter filter = IdFilter.of(List.of(params.item()));
        ValueTable values = InventoryTables.values();
        int source = bestSource(filter, values);
        int wornValue = bestWornValue(filter, values);
        if (wornValue >= 0 && !isBetterThanWorn(source, wornValue, values)) {
            return succeed();
        }
        if (source < 0) {
            return fail("в инвентаре нет предмета '" + params.item() + "': надевать нечего");
        }
        ItemStack stack = spirit.getInventory().getItem(source);
        EquipmentSlot slot = spirit.getEquipmentSlotForItem(stack);
        ItemStack worn = spirit.getItemBySlot(slot);
        ItemStack piece = spirit.getInventory().removeItem(source, 1);
        boolean copyInHand = slot == EquipmentSlot.MAINHAND && !spirit.mainHandOwned();
        if (!worn.isEmpty() && !copyInHand && !storeBack(worn, source)) {
            ItemStack rest = spirit.getInventory().getItem(source);
            if (rest.isEmpty()) {
                spirit.getInventory().setItem(source, piece);
            } else {
                rest.grow(piece.getCount());
            }
            return fail("нет места для обмена: слот '" + slot.getName() + "' занят, а инвентарь полон, "
                    + "ничего не перемещено");
        }
        if (slot == EquipmentSlot.MAINHAND) {
            spirit.holdInMainHand(piece);
        } else {
            spirit.setItemSlot(slot, piece);
        }
        spirit.setDropChance(slot, 1.0F);
        return succeed();
    }

    @Override
    public void cancel() {}

    @Override
    public String failureReason() {
        return reason;
    }

    private boolean isBetterThanWorn(int source, int wornValue, ValueTable values) {
        return source >= 0
                && values.valueOf(StackMapper.itemId(spirit.getInventory().getItem(source))) > wornValue;
    }

    private int bestWornValue(IdFilter filter, ValueTable values) {
        int best = -1;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack worn = spirit.getItemBySlot(slot);
            if (!worn.isEmpty() && filter.matches(StackMapper.itemId(worn))) {
                best = Math.max(best, values.valueOf(StackMapper.itemId(worn)));
            }
        }
        return best;
    }

    private int bestSource(IdFilter filter, ValueTable values) {
        Container inventory = spirit.getInventory();
        int best = -1;
        int bestValue = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !filter.matches(StackMapper.itemId(stack))) {
                continue;
            }
            int value = values.valueOf(StackMapper.itemId(stack));
            if (best < 0 || value > bestValue) {
                best = slot;
                bestValue = value;
            }
        }
        return best;
    }

    private boolean storeBack(ItemStack worn, int source) {
        Container inventory = spirit.getInventory();
        if (inventory.getItem(source).isEmpty()) {
            inventory.setItem(source, worn);
            return true;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, worn);
                return true;
            }
        }
        return false;
    }

    private ScriptStatus succeed() {
        status = ScriptStatus.SUCCEEDED;
        return status;
    }

    private ScriptStatus fail(String message) {
        status = ScriptStatus.FAILED;
        reason = message;
        return status;
    }
}
