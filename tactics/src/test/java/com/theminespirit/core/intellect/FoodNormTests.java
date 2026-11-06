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

        assertEquals(0, need.items(), "Пустому поселению еда не нужна: иначе ферма работает на несуществующих духов");
        assertEquals(0.0, need.nutrition(), "Пустому поселению сытность не нужна: нужда считается на душу");
    }

    @Test
    void perSoul_LargerPopulation_NeedsMore() {
        FoodNeed four = FoodNorm.perSoul(4, 1, TABLE, 1.5);
        FoodNeed eight = FoodNorm.perSoul(8, 1, TABLE, 1.5);

        assertTrue(
                eight.items() > four.items() && eight.nutrition() > four.nutrition(),
                "Нужда растёт с населением: иначе ферма недооценит голод большого поселения");
        assertEquals(
                12.0,
                eight.nutrition(),
                "Нужда 8 душ на сутки с пайком 1.5 — 12 сытности: спрос фермы считается от этого числа");
    }

    @Test
    void deficit_EmptyStock_EqualsNorm() {
        FoodDeficit deficit = FoodNorm.deficit(4, 1, 2, 1.5, 0.75, 0);

        assertEquals(13.5, deficit.need(), "Норма 4 взрослых и ребёнка на 2 суток: 2 × (4×1.5 + 0.75) = 13.5 сытности");
        assertEquals(
                0.0,
                deficit.stock(),
                "Пустой склад — запас ноль: дефицит равен норме, иначе интеллект недооценит голод");
        assertEquals(
                13.5, deficit.deficit(), "Дефицит пустого склада равен норме: спрос добычи еды растёт от этого числа");
    }

    @Test
    void deficit_StockAboveNorm_Zero() {
        FoodDeficit deficit = FoodNorm.deficit(3, 0, 1, 1.5, 0.75, 10);

        assertEquals(
                0.0,
                deficit.deficit(),
                "Запас выше нормы не создаёт отрицательного дефицита: излишек еды не превращается в долг");
    }

    @Test
    void deficit_PartialStock_Remainder() {
        FoodDeficit deficit = FoodNorm.deficit(3, 0, 1, 1.5, 0.75, 1.5);

        assertEquals(
                3.0,
                deficit.deficit(),
                "Норма 4.5 минус запас 1.5 — дефицит 3: спрос растёт от нехватки, а не от всего запаса");
    }

    @Test
    void deficit_ChildrenVersusAdults_NeedsLess() {
        FoodDeficit withChildren = FoodNorm.deficit(2, 2, 1, 1.5, 0.75, 0);
        FoodDeficit allAdults = FoodNorm.deficit(4, 0, 1, 1.5, 0.75, 0);

        assertTrue(
                withChildren.need() < allAdults.need(),
                "Дети едят меньше взрослых: норма поселения с детьми ниже, иначе ферма копит лишнее");
    }

    @Test
    void deficit_BadInputs_Throw() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FoodNorm.deficit(-1, 0, 1, 1.5, 0.75, 0),
                "Отрицательное число взрослых не описывает поселение: норма считается на душу");
        assertThrows(
                IllegalArgumentException.class,
                () -> FoodNorm.deficit(1, -1, 1, 1.5, 0.75, 0),
                "Отрицательное число детей не описывает поселение: норма считается на душу");
        assertThrows(
                IllegalArgumentException.class,
                () -> FoodNorm.deficit(1, 0, 0, 1.5, 0.75, 0),
                "Норма на ноль суток всегда выполнена: голод станет невидимым для спроса");
        assertThrows(
                IllegalArgumentException.class,
                () -> FoodNorm.deficit(1, 0, 1, 0, 0.75, 0),
                "Паёк взрослого ноль делает голод невидимым: норма задаётся данными > 0");
        assertThrows(
                IllegalArgumentException.class,
                () -> FoodNorm.deficit(1, 0, 1, 1.5, -1, 0),
                "Отрицательный паёк ребёнка переворачивает норму: спрос падает при голоде");
        assertThrows(
                IllegalArgumentException.class,
                () -> FoodNorm.deficit(1, 0, 1, 1.5, 0.75, -1),
                "Отрицательный запас невозможен: дефицит посчитался бы больше нормы");
    }

    @Test
    void rankScore_TastierFood_RanksHigher() {
        List<String> rank = FoodNorm.rankScore(TABLE);

        assertEquals(
                List.of("minecraft:bread", "minecraft:apple", "minecraft:cookie"),
                rank,
                "Что сытнее — то приоритетнее: хлеб 5 выше яблока 4, печенье 2 последнее");
    }

    @Test
    void table_EmptyMap_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new NutritionTable(Map.of()),
                "Пустая таблица сытности не годится: без данных о еде норма на душу не считается");

        assertTrue(
                error.getMessage().contains("пуста"),
                "Сообщение называет причину: при падении видно, что таблица не заполнена данными");
    }

    @Test
    void table_BadEntries_Throw() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new NutritionTable(null),
                "Норма без таблицы не считается: сытность еды — данные, а не ветка в коде");
        assertThrows(
                IllegalArgumentException.class,
                () -> new NutritionTable(Map.of("minecraft:bread", -1.0)),
                "Отрицательная сытность переворачивает ранг: худшая еда станет первой");
    }

    @Test
    void perSoul_NegativeInputs_Throw() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FoodNorm.perSoul(-1, 1, TABLE, 1.5),
                "Норма на отрицательное население не считается: душ не может быть отрицательно");
        assertThrows(
                IllegalArgumentException.class,
                () -> FoodNorm.perSoul(4, 0, TABLE, 1.5),
                "Норма на ноль суток всегда нулевая: голод станет невидимым для спроса");
        assertThrows(
                IllegalArgumentException.class,
                () -> FoodNorm.perSoul(4, 1, TABLE, -1),
                "Отрицательный паёк переворачивает нужду: ферма остановится при голоде");
    }

    @Test
    void best_TastiestPresent_Chosen() {
        assertEquals(
                "minecraft:bread",
                FoodNorm.best(TABLE, Map.of("minecraft:bread", 1, "minecraft:cookie", 9)),
                "Из еды склада берут самую сытную: хлеб 5 выше печенья 2, даже если печенья больше");
    }

    @Test
    void best_NoFoodInItems_Empty() {
        assertEquals(
                "",
                FoodNorm.best(TABLE, Map.of("minecraft:raw_iron", 64)),
                "Без еды в описи лучшая еда пустая: добыче нечего брать из сундука");
        assertEquals(
                "",
                FoodNorm.best(TABLE, Map.of("minecraft:bread", 0)),
                "Ноль хлеба — еды нет: пустой сундук не считается способом добычи");
    }

    @Test
    void best_NullInputs_Throw() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FoodNorm.best(null, Map.of()),
                "Лучшая еда без таблицы сытности не выбирается: сытность — данные");
        assertThrows(
                IllegalArgumentException.class,
                () -> FoodNorm.best(TABLE, null),
                "Лучшая еда без описи не выбирается: пустой набор задают пустой картой");
    }
}
