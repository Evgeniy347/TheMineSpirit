package com.theminespirit.core.planner;

import java.util.List;
import java.util.Map;

public final class FuelTable {
    public static final int COOK_TICKS = 200;

    private static final Map<String, Integer> BURN_TICKS = Map.of(
            "coal", 1600,
            "charcoal", 1600,
            "oak_log", 300,
            "oak_planks", 300);

    private static final List<String> PREFERENCE = List.of("coal", "charcoal", "oak_log", "oak_planks");

    private FuelTable() {
    }

    public static int burnTicks(String item) {
        return BURN_TICKS.getOrDefault(item, 0);
    }

    public static List<String> preferenceOrder() {
        return PREFERENCE;
    }

    public static int fuelNeeded(int count, int burnTicks) {
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "Количество плавки должно быть больше 0, а получено " + count + ": топлива на ноль предметов не нужно");
        }
        if (burnTicks <= 0) {
            throw new IllegalArgumentException(
                    "Время горения топлива должно быть больше 0, а получено " + burnTicks + ": таким топливом печь не топится");
        }
        return (int) ((count * (long) COOK_TICKS + burnTicks - 1) / burnTicks);
    }
}
