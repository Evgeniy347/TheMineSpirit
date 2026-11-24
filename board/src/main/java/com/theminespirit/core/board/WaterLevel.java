package com.theminespirit.core.board;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public record WaterLevel(int blocks, int median) {
    public WaterLevel {
        if (blocks < 1) {
            throw new IllegalArgumentException("Колонок воды " + blocks + ": уровень воды без колонок не считается");
        }
    }

    public int minSettlementLevel() {
        return median + 1;
    }

    public static Optional<WaterLevel> of(int[] levels, int minBlocks) {
        Objects.requireNonNull(levels, "Высоты воды не могут быть null: без них нет медианы");
        if (minBlocks < 1) {
            throw new IllegalArgumentException(
                    "Минимум колонок воды " + minBlocks + ": порог обязан быть положительным");
        }
        if (levels.length < minBlocks) {
            return Optional.empty();
        }
        int[] sorted = levels.clone();
        Arrays.sort(sorted);
        return Optional.of(new WaterLevel(sorted.length, sorted[sorted.length / 2]));
    }
}
