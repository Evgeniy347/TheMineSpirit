package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FoodNormTests {
    private static final NutritionTable TABLE =
            new NutritionTable(Map.of("minecraft:bread", 5.0, "minecraft:apple", 4.0, "minecraft:cookie", 2.0));

    @Test
    void perSoul_ZeroPopulation_NeedsNothing() {
        FoodNeed need = FoodNorm.perSoul(0, 1, TABLE, 1.5);

        assertEquals(0, need.items(),
                "Пустому поселению еда не нужна: иначе ферма работает на несуществующих духов");
        assertEquals(0.0, need.nutrition(),
                "Пустому поселению сытность не нужна: нужда считается на душу");
    }

    @Test
    void perSoul_LargerPopulation_NeedsMore() {
        FoodNeed four = FoodNorm.perSoul(4, 1, TABLE, 1.5);
        FoodNeed eight = FoodNorm.perSoul(8, 1, TABLE, 1.5);

        assertTrue(eight.items() > four.items() && eight.nutrition() > four.nutrition(),
                "Нужда растёт с населением: иначе ферма недооценит голод большого поселения");
        assertEquals(12.0, eight.nutrition(),
                "Нужда 8 душ на сутки с пайком 1.5 — 12 сытности: спрос фермы считается от этого числа");
    }

    @Test
    void rankScore_TastierFood_RanksHigher() {
        List<String> rank = FoodNorm.rankScore(TABLE);

        assertEquals(List.of("minecraft:bread", "minecraft:apple", "minecraft:cookie"), rank,
                "Что сытнее — то приоритетнее: хлеб 5 выше яблока 4, печенье 2 последнее");
    }

    @Test
    void table_EmptyMap_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new NutritionTable(Map.of()),
                "Пустая таблица сытности не годится: без данных о еде норма на душу не считается");

        assertTrue(error.getMessage().contains("пуста"),
                "Сообщение называет причину: при падении видно, что таблица не заполнена данными");
    }

    @Test
    void table_BadEntries_Throw() {
        assertThrows(IllegalArgumentException.class, () -> new NutritionTable(null),
                "Норма без таблицы не считается: сытность еды — данные, а не ветка в коде");
        assertThrows(IllegalArgumentException.class,
                () -> new NutritionTable(Map.of("minecraft:bread", -1.0)),
                "Отрицательная сытность переворачивает ранг: худшая еда станет первой");
    }

    @Test
    void perSoul_NegativeInputs_Throw() {
        assertThrows(IllegalArgumentException.class, () -> FoodNorm.perSoul(-1, 1, TABLE, 1.5),
                "Норма на отрицательное население не считается: душ не может быть отрицательно");
        assertThrows(IllegalArgumentException.class, () -> FoodNorm.perSoul(4, 0, TABLE, 1.5),
                "Норма на ноль суток всегда нулевая: голод станет невидимым для спроса");
        assertThrows(IllegalArgumentException.class, () -> FoodNorm.perSoul(4, 1, TABLE, -1),
                "Отрицательный паёк переворачивает нужду: ферма остановится при голоде");
    }
}
