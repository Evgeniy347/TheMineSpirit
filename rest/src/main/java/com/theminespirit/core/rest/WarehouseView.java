package com.theminespirit.core.rest;

import com.theminespirit.core.board.WarehouseOrder;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record WarehouseView(
        int stage,
        int requiredLevel,
        int builtLevel,
        boolean needed,
        @Nullable WarehouseOrder order,
        Map<String, Integer> materials) {
    public WarehouseView {
        if (stage < 1 || requiredLevel < 1) {
            throw new IllegalArgumentException(
                    "Этап " + stage + " и требуемый уровень " + requiredLevel + ": отсчёт с первого");
        }
        if (builtLevel < 0) {
            throw new IllegalArgumentException("Построенный уровень склада " + builtLevel + " отрицателен");
        }
        materials = Map.copyOf(Objects.requireNonNull(
                materials, "Материалы склада не могут быть null: неизвестные задают пустой картой"));
    }
}
