package com.theminespirit.core.intellect;

import java.util.Map;

public record NutritionTable(Map<String, Double> nutrition) {
    public NutritionTable {
        if (nutrition == null) {
            throw new IllegalArgumentException(
                    "Таблица сытности не может быть null: ранг еды считается по данным, а не по коду");
        }
        if (nutrition.isEmpty()) {
            throw new IllegalArgumentException(
                    "Таблица сытности пуста: без данных о еде норма на душу не считается");
        }
        for (Map.Entry<String, Double> entry : nutrition.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException(
                        "Еда без имени в таблице сытности: ранг ссылается на предметы по именам");
            }
            Double value = entry.getValue();
            if (value == null || Double.isNaN(value) || Double.isInfinite(value) || value <= 0) {
                throw new IllegalArgumentException("Сытность " + entry.getKey() + " = " + value
                        + ": обязана быть конечным числом > 0, иначе ранг еды неверен");
            }
        }
        nutrition = Map.copyOf(nutrition);
    }
}
