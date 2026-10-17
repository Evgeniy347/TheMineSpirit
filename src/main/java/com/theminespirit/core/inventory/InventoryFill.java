package com.theminespirit.core.inventory;

public record InventoryFill(int occupiedSlots, int capacity, int extraItems, int storedItems) {
    public static final InventoryFill EMPTY = new InventoryFill(0, 0, 0, 0);

    public InventoryFill {
        if (occupiedSlots < 0) {
            throw new IllegalArgumentException("Занятых ячеек " + occupiedSlots
                    + ": число не может быть отрицательным");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("Вместимость " + capacity + ": число не может быть отрицательным");
        }
        if (occupiedSlots > capacity) {
            throw new IllegalArgumentException("Занятых ячеек " + occupiedSlots + " при вместимости " + capacity
                    + ": занятое не может превышать вместимость");
        }
        if (extraItems < 0 || storedItems < 0) {
            throw new IllegalArgumentException("Лишних " + extraItems + " из " + storedItems
                    + ": предметов не может быть отрицательное число");
        }
        if (extraItems > storedItems) {
            throw new IllegalArgumentException("Лишних предметов " + extraItems + " при лежащих " + storedItems
                    + ": лишнее — часть лежащего, а не больше него");
        }
        if ((occupiedSlots == 0) != (storedItems == 0)) {
            throw new IllegalArgumentException("Занятых ячеек " + occupiedSlots + " при лежащих предметах "
                    + storedItems + ": пустой инвентарь задаётся нулями, непустой имеет и ячейки, и предметы");
        }
    }

    public double occupiedFraction() {
        return capacity == 0 ? 0.0 : (double) occupiedSlots / capacity;
    }

    public double extraFraction() {
        return storedItems == 0 ? 0.0 : (double) extraItems / storedItems;
    }

    public int freeSlots() {
        return capacity - occupiedSlots;
    }
}
