package com.theminespirit.core.intellect;

import com.theminespirit.core.inventory.InventoryFill;
import java.util.Objects;

public final class TaskRetention {
    private TaskRetention() {}

    public static boolean holds(ActivityId activity, InventoryFill fill, InventoryNormTable table) {
        Objects.requireNonNull(fill, "Заполнение инвентаря не может быть null: по нему решают, держать ли задачу");
        return holds(activity, fill.freeSlots(), table);
    }

    public static boolean holds(ActivityId activity, int freeSlots, InventoryNormTable table) {
        Objects.requireNonNull(activity, "Деятельность не может быть null: порог удержания ищут по id");
        Objects.requireNonNull(table, "Таблица нормы не может быть null: порог удержания — данные");
        if (freeSlots < 0) {
            throw new IllegalArgumentException("Свободных ячеек " + freeSlots + ": число не может быть отрицательным");
        }
        return freeSlots > table.releaseFreeSlots(activity);
    }
}
