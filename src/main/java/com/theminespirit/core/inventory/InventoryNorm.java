package com.theminespirit.core.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InventoryNorm {
    private InventoryNorm() {
    }

    public static List<Slice> extra(List<Stack> inventory, BaseKit kit, List<String> taskNeeds,
            ValueTable values) {
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: норма считается по его слотам");
        Objects.requireNonNull(kit, "Базовый набор не может быть null: без него норма не определена");
        Objects.requireNonNull(taskNeeds, "Нужды задачи не могут быть null: пустые задают пустым списком");
        Objects.requireNonNull(values, "Таблица ценностей не может быть null: по ней набор выбирает лучшее");
        Protected protection = Protected.of(taskNeeds, inventory, kit, values);
        List<Slice> extra = new ArrayList<>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.get(slot) == null) {
                continue;
            }
            int give = protection.giveAway(slot);
            if (give > 0) {
                extra.add(new Slice(slot, give));
            }
        }
        return List.copyOf(extra);
    }

    public static InventoryFill fill(List<Stack> inventory, BaseKit kit, List<String> taskNeeds,
            ValueTable values) {
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: заполнение считается по его слотам");
        List<Slice> surplus = extra(inventory, kit, taskNeeds, values);
        int occupied = 0;
        int stored = 0;
        for (Stack stack : inventory) {
            if (stack != null) {
                occupied++;
                stored += stack.count();
            }
        }
        int extraItems = 0;
        for (Slice slice : surplus) {
            extraItems += slice.count();
        }
        return new InventoryFill(occupied, inventory.size(), extraItems, stored);
    }
}
