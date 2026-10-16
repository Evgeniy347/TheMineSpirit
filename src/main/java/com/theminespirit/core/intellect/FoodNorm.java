package com.theminespirit.core.intellect;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class FoodNorm {
    private FoodNorm() {
    }

    public static FoodNeed perSoul(int population, double days, NutritionTable table, double ration) {
        if (population < 0) {
            throw new IllegalArgumentException(
                    "Население = " + population + ": норма еды считается на душу, душ не может быть отрицательно");
        }
        if (Double.isNaN(days) || Double.isInfinite(days) || days <= 0) {
            throw new IllegalArgumentException(
                    "Запас считается на " + days + " суток: срок обязан быть конечным и > 0");
        }
        if (table == null) {
            throw new IllegalArgumentException("Норма еды без таблицы сытности не считается: сытность — данные");
        }
        if (Double.isNaN(ration) || Double.isInfinite(ration) || ration <= 0) {
            throw new IllegalArgumentException(
                    "Паёк = " + ration + ": суточная норма на душу обязана быть конечной и > 0");
        }
        double need = population * days * ration;
        double best = table.nutrition().values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        return new FoodNeed((long) Math.ceil(need / best), need);
    }

    public static List<String> rankScore(NutritionTable table) {
        if (table == null) {
            throw new IllegalArgumentException("Ранг еды без таблицы сытности не строится: сытность — данные");
        }
        return table.nutrition().entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();
    }

    public static String best(NutritionTable table, Map<String, Integer> items) {
        if (table == null) {
            throw new IllegalArgumentException("Лучшая еда без таблицы сытности не выбирается: сытность — данные");
        }
        if (items == null) {
            throw new IllegalArgumentException("Лучшая еда без описи не выбирается: пустой набор задают пустой картой");
        }
        for (String id : rankScore(table)) {
            Integer count = items.get(id);
            if (count != null && count > 0) {
                return id;
            }
        }
        return "";
    }
}
