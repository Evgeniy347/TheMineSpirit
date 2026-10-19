package com.theminespirit.core.processing;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public record ProcessingStock(
        int charcoal,
        int planks,
        int sticks,
        double cookedNutrition,
        Map<String, Integer> rawFoods,
        Map<String, Integer> logs) {

    public static final ProcessingStock NONE = new ProcessingStock(0, 0, 0, 0.0, Map.of(), Map.of());

    public ProcessingStock {
        if (charcoal < 0 || planks < 0 || sticks < 0) {
            throw new IllegalArgumentException("Запас переработки не может быть отрицательным: уголь " + charcoal
                    + ", доски " + planks + ", палки " + sticks);
        }
        if (Double.isNaN(cookedNutrition) || Double.isInfinite(cookedNutrition) || cookedNutrition < 0) {
            throw new IllegalArgumentException("Сытность готовой еды " + cookedNutrition
                    + ": обязана быть конечной и >= 0, иначе спрос готовки врёт");
        }
        rawFoods = copyCounts(rawFoods, "сырой еды");
        logs = copyCounts(logs, "брёвен");
    }

    public boolean hasRawFood() {
        return !rawFoods.isEmpty();
    }

    public boolean hasLogs() {
        return !logs.isEmpty();
    }

    public boolean hasPlanks() {
        return planks > 0;
    }

    public Optional<String> bestLogItem() {
        return logs.entrySet().stream()
                .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(entry -> entry.getKey(), Comparator.reverseOrder()))
                .map(Map.Entry::getKey);
    }

    public Optional<String> plankProduct() {
        return bestLogItem().flatMap(ProcessingStock::planksOf);
    }

    public static Optional<String> planksOf(@Nullable String logId) {
        if (logId == null || logId.isBlank()) {
            return Optional.empty();
        }
        int colon = logId.indexOf(':');
        String prefix = colon >= 0 ? logId.substring(0, colon + 1) : "";
        String name = colon >= 0 ? logId.substring(colon + 1) : logId;
        if (name.startsWith("stripped_")) {
            name = name.substring("stripped_".length());
        }
        for (String suffix : new String[] {"_log", "_wood", "_stem", "_hyphae"}) {
            if (name.endsWith(suffix)) {
                return Optional.of(prefix + name.substring(0, name.length() - suffix.length()) + "_planks");
            }
        }
        return Optional.empty();
    }

    private static Map<String, Integer> copyCounts(Map<String, Integer> source, String what) {
        if (source == null) {
            throw new IllegalArgumentException(
                    "Опись " + what + " не может быть null: пустой набор задают пустой картой");
        }
        Map<String, Integer> copy = new HashMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("Предмет без имени в описи " + what + ": счёт идёт по id предметов");
            }
            if (entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException("Предметов " + entry.getKey() + " = " + entry.getValue() + ": счёт "
                        + what + " обязан быть >= 0");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(copy);
    }
}
