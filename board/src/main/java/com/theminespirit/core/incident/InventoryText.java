package com.theminespirit.core.incident;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class InventoryText {
    private InventoryText() {}

    public static String of(Map<String, Integer> inventory) {
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: пустой инвентарь задают пустой картой");
        Map<String, Integer> sorted = new TreeMap<>();
        inventory.forEach((item, count) -> {
            if (item == null || item.isBlank() || count == null || count < 0) {
                throw new IllegalArgumentException("Предмет инвентаря '" + item + "' × " + count
                        + "' назван пусто или отрицательно: строка провала перечисляет реальные предметы");
            }
            if (count > 0) {
                sorted.put(item, count);
            }
        });
        return sorted.isEmpty() ? "пусто" : sorted.toString();
    }
}
