package com.theminespirit.core.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InventorySpace {
    private InventorySpace() {
    }

    public static boolean holds(List<Stack> inventory, Stack incoming) {
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: проверка вместимости считает слоты инвентаря");
        Objects.requireNonNull(incoming, "Входящая стопка не может быть null: без неё нечего проверять");
        return holds(inventory, List.of(incoming));
    }

    public static boolean holds(List<Stack> inventory, List<Stack> incoming) {
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: проверка вместимости считает слоты инвентаря");
        Objects.requireNonNull(incoming, "Список входящих не может быть null: пустой список задаётся пустым списком");
        List<Stack> slots = new ArrayList<>(inventory.size());
        for (Stack stack : inventory) {
            slots.add(stack);
        }
        while (slots.size() < OverflowPolicy.INVENTORY_SLOTS) {
            slots.add(null);
        }
        for (Stack item : incoming) {
            if (item == null) {
                continue;
            }
            int remaining = item.count();
            for (int index = 0; index < slots.size() && remaining > 0; index++) {
                Stack current = slots.get(index);
                if (current != null && current.sameKindAs(item)) {
                    int space = current.freeSpace();
                    int take = Math.min(space, remaining);
                    if (take > 0) {
                        slots.set(index, current.withCount(current.count() + take));
                        remaining -= take;
                    }
                }
            }
            while (remaining > 0) {
                int empty = -1;
                for (int index = 0; index < slots.size(); index++) {
                    if (slots.get(index) == null) {
                        empty = index;
                        break;
                    }
                }
                if (empty < 0) {
                    return false;
                }
                int take = Math.min(item.maxStack(), remaining);
                slots.set(empty, item.withCount(take));
                remaining -= take;
            }
        }
        return true;
    }
}
