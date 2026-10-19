package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.armor.ArmorChoice;
import com.theminespirit.core.armor.ArmorPiece;
import com.theminespirit.core.armor.ArmorSlot;
import com.theminespirit.core.armor.ArmorTable;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class ArmorEquipPolicy {
    private static final Map<EquipmentSlot, ArmorSlot> SLOTS = armorSlots();
    private static final EquipmentSlot[] ORDER = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final SpiritEntity spirit;
    private long lastChange = -1;
    private long lastCheck = Long.MIN_VALUE;
    private long swaps;
    private long deferred;

    public ArmorEquipPolicy(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public void tick() {
        if (spirit.level().isClientSide()) {
            return;
        }
        if (spirit.tactics().wantsControl()) {
            deferred++;
            return;
        }
        long now = spirit.level().getGameTime();
        long changes = spirit.getInventory().changeCount();
        ArmorTable table = InventoryTables.armor();
        boolean changed = changes != lastChange;
        boolean due = now - lastCheck >= table.checkIntervalTicks();
        if (!changed && !due) {
            return;
        }
        lastChange = changes;
        lastCheck = now;
        equipBest(table);
    }

    public long swaps() {
        return swaps;
    }

    public long deferred() {
        return deferred;
    }

    private void equipBest(ArmorTable table) {
        for (EquipmentSlot equipmentSlot : ORDER) {
            ArmorSlot slot = SLOTS.get(equipmentSlot);
            ItemStack worn = spirit.getItemBySlot(equipmentSlot);
            ArmorPiece wornPiece = piece(worn, slot);
            int bestSource = -1;
            ArmorPiece best = null;
            Container inventory = spirit.getInventory();
            for (int index = 0; index < inventory.getContainerSize(); index++) {
                ArmorPiece candidate = piece(inventory.getItem(index), slot);
                if (candidate == null) {
                    continue;
                }
                if (best == null || ArmorChoice.compare(candidate, best, table) > 0) {
                    best = candidate;
                    bestSource = index;
                }
            }
            if (best == null || !ArmorChoice.isStrictlyBetter(best, wornPiece, table)) {
                continue;
            }
            if (swap(equipmentSlot, bestSource)) {
                swaps++;
                lastChange = spirit.getInventory().changeCount();
            }
        }
    }

    private boolean swap(EquipmentSlot equipmentSlot, int source) {
        Container inventory = spirit.getInventory();
        ItemStack worn = spirit.getItemBySlot(equipmentSlot);
        ItemStack piece = inventory.removeItem(source, 1);
        if (piece.isEmpty()) {
            return false;
        }
        spirit.setItemSlot(equipmentSlot, piece);
        spirit.setDropChance(equipmentSlot, 1.0F);
        if (!worn.isEmpty() && !storeBack(worn, source)) {
            spirit.setItemSlot(equipmentSlot, worn);
            ItemStack rest = inventory.getItem(source);
            if (rest.isEmpty()) {
                inventory.setItem(source, piece);
            } else {
                rest.grow(piece.getCount());
            }
            return false;
        }
        return true;
    }

    private boolean storeBack(ItemStack worn, int source) {
        Container inventory = spirit.getInventory();
        if (inventory.getItem(source).isEmpty()) {
            inventory.setItem(source, worn);
            return true;
        }
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            if (inventory.getItem(index).isEmpty()) {
                inventory.setItem(index, worn);
                return true;
            }
        }
        return false;
    }

    private static ArmorPiece piece(ItemStack stack, ArmorSlot slot) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem armor)) {
            return null;
        }
        if (SLOTS.get(armor.getEquipmentSlot()) != slot) {
            return null;
        }
        return new ArmorPiece(
                StackMapper.itemId(stack),
                slot,
                armor.getDefense(),
                armor.getToughness(),
                protectionLevel(stack),
                durabilityFraction(stack));
    }

    private static double durabilityFraction(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            return 1.0;
        }
        return 1.0 - (double) stack.getDamageValue() / stack.getMaxDamage();
    }

    private static int protectionLevel(ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        int total = 0;
        for (var entry : enchantments.entrySet()) {
            if (entry.getKey().is(Enchantments.PROTECTION)
                    || entry.getKey().is(Enchantments.FIRE_PROTECTION)
                    || entry.getKey().is(Enchantments.BLAST_PROTECTION)
                    || entry.getKey().is(Enchantments.PROJECTILE_PROTECTION)) {
                total += entry.getIntValue();
            }
        }
        return total;
    }

    private static Map<EquipmentSlot, ArmorSlot> armorSlots() {
        Map<EquipmentSlot, ArmorSlot> slots = new EnumMap<>(EquipmentSlot.class);
        slots.put(EquipmentSlot.HEAD, ArmorSlot.HEAD);
        slots.put(EquipmentSlot.CHEST, ArmorSlot.CHEST);
        slots.put(EquipmentSlot.LEGS, ArmorSlot.LEGS);
        slots.put(EquipmentSlot.FEET, ArmorSlot.FEET);
        return slots;
    }
}
