package com.theminespirit.core.farming;

import java.util.Objects;

public final class SeedPolicy {
    private SeedPolicy() {}

    public static Decision forReplant(CropKind kind, int plantItemsInInventory) {
        Objects.requireNonNull(kind, "Вид культуры не может быть null: без него неизвестно, что пересаживать");
        if (kind == CropKind.ANY) {
            throw new IllegalArgumentException(
                    "Пересадка вида any невозможна: any означает «любой вид», а в клетку сажают собранный вид");
        }
        if (plantItemsInInventory < 0) {
            throw new IllegalArgumentException(
                    "Предметов в инвентаре не может быть отрицательно: " + plantItemsInInventory);
        }
        return plantItemsInInventory > 0 ? Decision.PLANT : Decision.OUT_OF_SEEDS;
    }

    public static int forPlanting(CropKind kind, int plantItemsInInventory, int cellsLeft) {
        Objects.requireNonNull(kind, "Вид культуры не может быть null: без него неизвестно, что сажать");
        if (kind == CropKind.ANY) {
            throw new IllegalArgumentException(
                    "Посадка вида any невозможна: any означает «любой вид», а сажают конкретный вид");
        }
        if (plantItemsInInventory < 0) {
            throw new IllegalArgumentException(
                    "Предметов в инвентаре не может быть отрицательно: " + plantItemsInInventory);
        }
        if (cellsLeft < 0) {
            throw new IllegalArgumentException("Оставшихся клеток не может быть отрицательно: " + cellsLeft);
        }
        return Math.min(plantItemsInInventory, cellsLeft);
    }

    public enum Decision {
        PLANT,
        OUT_OF_SEEDS
    }
}
