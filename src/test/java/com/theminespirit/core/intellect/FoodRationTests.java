package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class FoodRationTests {
    private static final NutritionTable TABLE = new NutritionTable(Map.of(
            "minecraft:bread", 5.0,
            "minecraft:apple", 4.0,
            "minecraft:cooked_beef", 8.0,
            "minecraft:cooked_chicken", 6.0));

    @Test
    void choose_CookedAndRaw_PrefersMostNutritiousReady() {
        FoodRation.Choice choice = FoodRation.choose(
                Map.of("bread", 2, "cooked_beef", 1, "beef", 5), TABLE, false).orElseThrow();

        assertEquals("minecraft:cooked_beef", choice.item(),
                "Из готовой еды берут самую сытную: cooked_beef (8) сытнее хлеба (5)");
        assertFalse(choice.needsCooking(),
                "Готовая еда не требует готовки: сырьё рядом (beef) не должно перебивать готовый продукт");
    }

    @Test
    void choose_OnlyRawWithStation_NeedsCooking() {
        FoodRation.Choice choice = FoodRation.choose(Map.of("beef", 3), TABLE, true).orElseThrow();

        assertEquals("minecraft:beef", choice.item(),
                "При одной станции и только сырье выбирают сырьё на готовку: иначе дух ел бы сырое при печи рядом");
        assertTrue(choice.needsCooking(),
                "Сырьё при наличии станции сначала идёт на готовку, а не в раздачу");
    }

    @Test
    void choose_OnlyRawWithoutStation_EatsRaw() {
        FoodRation.Choice choice = FoodRation.choose(Map.of("beef", 3), TABLE, false).orElseThrow();

        assertEquals("minecraft:beef", choice.item(),
                "Без станции голодный дух берёт сырьё со склада: голод хуже сырой еды");
        assertFalse(choice.needsCooking(),
                "Без станции сырьё готовить негде, оно раздаётся как есть");
    }

    @Test
    void choose_TwoRaws_PicksHigherCookedSatiety() {
        FoodRation.Choice choice = FoodRation.choose(Map.of("chicken", 2, "beef", 2), TABLE, false).orElseThrow();

        assertEquals("minecraft:beef", choice.item(),
                "Из сырья берут то, чей готовый продукт сытнее: cooked_beef (8) против cooked_chicken (6)");
    }

    @Test
    void choose_EmptyStock_None() {
        assertTrue(FoodRation.choose(Map.of(), TABLE, true).isEmpty(),
                "С пустым складом раздавать нечего: задание не выдаётся, иначе withdraw провалится вхолостую");
    }

    @Test
    void choose_NonFoodStock_None() {
        assertTrue(FoodRation.choose(Map.of("dirt", 64), TABLE, false).isEmpty(),
                "Непищевые предметы не раздают как еду: иначе дух понесёт землю вместо обеда");
    }

    @Test
    void choose_Tie_PicksLexicographicallySmallest() {
        NutritionTable tie = new NutritionTable(Map.of("minecraft:bread", 5.0, "minecraft:apple", 5.0));
        FoodRation.Choice choice = FoodRation.choose(Map.of("bread", 1, "apple", 1), tie, false).orElseThrow();

        assertEquals("minecraft:apple", choice.item(),
                "Ничья по сытности разрешается меньшим id: выбор обязан быть детерминированным");
    }

    @Test
    void due_BelowNorm_True() {
        assertTrue(FoodRation.due(2.0, 6.0),
                "Личной еды меньше нормы — дух идёт пополнять запас со склада");
    }

    @Test
    void due_AtNorm_False() {
        assertFalse(FoodRation.due(6.0, 6.0),
                "Личной еды ровно норма — пополнять нечего: порог достигнут, а не перейдён");
    }

    @Test
    void due_InvalidInputs_Throw() {
        assertThrows(IllegalArgumentException.class, () -> FoodRation.due(-1, 6),
                "Отрицательная личная еда невозможна: ошибка вместо тихого дефицита");
        assertThrows(IllegalArgumentException.class, () -> FoodRation.due(1, 0),
                "Нулевая норма не задаёт дефицит: ошибка вместо деления на ноль");
    }

    @Property
    void choose_ReadyStock_PicksMaximumSatiety(
            @ForAll @IntRange(min = 0, max = 20) int bread,
            @ForAll @IntRange(min = 0, max = 20) int apple,
            @ForAll @IntRange(min = 0, max = 20) int cookedBeef) {
        Map<String, Integer> stock = new HashMap<>();
        if (bread > 0) {
            stock.put("bread", bread);
        }
        if (apple > 0) {
            stock.put("apple", apple);
        }
        if (cookedBeef > 0) {
            stock.put("cooked_beef", cookedBeef);
        }
        var choice = FoodRation.choose(stock, TABLE, false);
        if (stock.isEmpty()) {
            assertTrue(choice.isEmpty(),
                    "Пустой склад не даёт выбора: раздавать нечего");
            return;
        }
        FoodRation.Choice picked = choice.orElseThrow();
        double pickedSatiety = TABLE.nutrition().get(picked.item());
        for (String id : stock.keySet()) {
            assertTrue(pickedSatiety >= TABLE.nutrition().get("minecraft:" + id),
                    "Выбранная готовая еда " + picked.item() + " (" + pickedSatiety + ") сытнее или равна "
                            + id + " (" + TABLE.nutrition().get("minecraft:" + id) + "): иначе раздача отдаёт "
                            + "менее сытный продукт при более сытном на складе");
        }
    }
}
