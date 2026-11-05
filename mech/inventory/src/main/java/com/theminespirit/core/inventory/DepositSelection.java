package com.theminespirit.core.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DepositSelection {
    private DepositSelection() {}

    public static List<Slice> surplus(List<Stack> inventory, BaseKit kit, ValueTable values, int protectedSlot) {
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: сдача считается по его слотам");
        Objects.requireNonNull(kit, "Базовый набор не может быть null: без него дух сдал бы своё снаряжение");
        Objects.requireNonNull(
                values,
                "Таблица ценностей не может быть null: по ней набор отличает лучший " + "инструмент от худшего");
        if (protectedSlot < -1) {
            throw new IllegalArgumentException("Защищённый слот " + protectedSlot + " отрицательный: "
                    + "слоты инвентаря нумеруются с нуля, -1 означает «защищённого слота нет»");
        }
        Protected protection = Protected.of(List.of(), inventory, kit, values);
        List<Slice> slices = new ArrayList<>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (slot == protectedSlot) {
                continue;
            }
            int give = protection.giveAway(slot);
            if (give > 0) {
                slices.add(new Slice(slot, give));
            }
        }
        return slices;
    }
}
