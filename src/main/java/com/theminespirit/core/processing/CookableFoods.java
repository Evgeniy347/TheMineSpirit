package com.theminespirit.core.processing;

import com.theminespirit.core.intellect.NutritionTable;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CookableFoods {
    private static final Map<String, String> RAW_TO_COOKED = Map.ofEntries(
            Map.entry("minecraft:beef", "minecraft:cooked_beef"),
            Map.entry("minecraft:porkchop", "minecraft:cooked_porkchop"),
            Map.entry("minecraft:chicken", "minecraft:cooked_chicken"),
            Map.entry("minecraft:mutton", "minecraft:cooked_mutton"),
            Map.entry("minecraft:rabbit", "minecraft:cooked_rabbit"),
            Map.entry("minecraft:cod", "minecraft:cooked_cod"),
            Map.entry("minecraft:salmon", "minecraft:cooked_salmon"),
            Map.entry("minecraft:potato", "minecraft:baked_potato"));

    private static final Set<String> COOKED = Set.copyOf(new HashSet<>(RAW_TO_COOKED.values()));

    private static final Set<String> FISH = Set.of(
            "minecraft:cod", "minecraft:salmon", "minecraft:pufferfish", "minecraft:tropical_fish");

    private CookableFoods() {
    }

    public static boolean isFish(String item) {
        return item != null && FISH.contains(item);
    }

    public static boolean isCookableFish(String item) {
        return isFish(item) && isRaw(item);
    }

    public static boolean isRaw(String item) {
        return item != null && RAW_TO_COOKED.containsKey(item);
    }

    public static boolean isCooked(String item) {
        return item != null && (COOKED.contains(item) || item.startsWith("minecraft:cooked_")
                || item.equals("minecraft:baked_potato"));
    }

    public static Optional<String> cooked(String raw) {
        return Optional.ofNullable(RAW_TO_COOKED.get(raw));
    }

    public static Optional<String> best(Map<String, Integer> rawFoods, NutritionTable table) {
        if (rawFoods == null) {
            throw new IllegalArgumentException("Выбор сырья без описи не идёт: пустой набор задают пустой картой");
        }
        return rawFoods.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .filter(entry -> RAW_TO_COOKED.containsKey(entry.getKey()))
                .map(entry -> Map.entry(RAW_TO_COOKED.get(entry.getKey()), satiety(table, RAW_TO_COOKED.get(entry.getKey()))))
                .max(Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey);
    }

    private static double satiety(NutritionTable table, String cooked) {
        if (table == null) {
            return 0;
        }
        Double value = table.nutrition().get(cooked);
        return value == null ? 0 : value;
    }
}
