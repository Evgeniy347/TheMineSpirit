package com.theminespirit.core.planner;

import java.util.List;

public final class FuelTable {
    public static final int COOK_TICKS = 200;

    private static final List<FuelSpec> FUELS = List.of(
            new FuelSpec("coal", 1600),
            new FuelSpec("oak_planks", 300),
            new FuelSpec("spruce_planks", 300),
            new FuelSpec("birch_planks", 300),
            new FuelSpec("jungle_planks", 300),
            new FuelSpec("acacia_planks", 300),
            new FuelSpec("dark_oak_planks", 300),
            new FuelSpec("mangrove_planks", 300),
            new FuelSpec("cherry_planks", 300),
            new FuelSpec("bamboo_planks", 300),
            new FuelSpec("charcoal", 1600),
            new FuelSpec("oak_log", 300),
            new FuelSpec("spruce_log", 300),
            new FuelSpec("birch_log", 300),
            new FuelSpec("jungle_log", 300),
            new FuelSpec("acacia_log", 300),
            new FuelSpec("dark_oak_log", 300),
            new FuelSpec("mangrove_log", 300),
            new FuelSpec("cherry_log", 300),
            new FuelSpec("stick", 100));

    private FuelTable() {
    }

    public static int burnTicks(String item) {
        for (FuelSpec fuel : FUELS) {
            if (fuel.item().equals(item)) {
                return fuel.burnTicks();
            }
        }
        return 0;
    }

    public static List<String> preferenceOrder() {
        return FUELS.stream().map(FuelSpec::item).toList();
    }

    public static int fuelNeeded(int count, int burnTicks) {
        return fuelNeeded(count, burnTicks, COOK_TICKS);
    }

    public static int fuelNeeded(int count, int burnTicks, int cookTicks) {
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "Количество плавки должно быть больше 0, а получено " + count + ": топлива на ноль предметов не нужно");
        }
        if (burnTicks <= 0) {
            throw new IllegalArgumentException(
                    "Время горения топлива должно быть больше 0, а получено " + burnTicks + ": таким топливом печь не топится");
        }
        if (cookTicks <= 0) {
            throw new IllegalArgumentException(
                    "Время плавки должно быть больше 0, а получено " + cookTicks + ": по нему считается расход топлива");
        }
        return (int) ((count * (long) cookTicks + burnTicks - 1) / burnTicks);
    }

    private record FuelSpec(String item, int burnTicks) {
    }
}
