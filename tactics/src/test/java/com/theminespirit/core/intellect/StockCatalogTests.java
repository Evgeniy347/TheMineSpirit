package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StockCatalogTests {
    private static final NutritionTable TABLE =
            new NutritionTable(Map.of("minecraft:bread", 5.0, "minecraft:apple", 4.0));

    @Test
    void tally_ChestWithFoodAndIron_CountsMatch() {
        StockCatalog catalog = new StockCatalog(TABLE);

        StockLots lots = catalog.tally(Map.of(
                "minecraft:bread", 5, "minecraft:iron_ingot", 3, "minecraft:iron_sword", 1, "minecraft:dirt", 64));

        assertEquals(
                5,
                lots.foodItems(),
                "Сундук с 5 хлебом даёт 5 еды: иначе спрос фермы не видит запас и гонит духов в поле зря"
                        + " При смене tally_ChestWithFoodAndIron_CountsMatch проверь это утверждение.");
        assertEquals(
                25.0,
                lots.foodNutrition(),
                "Сытность 5 хлебов по 5 — 25: спрос фермы считается от сытности, а не от штук"
                        + " Иначе тест tally_ChestWithFoodAndIron_CountsMatch перестанет ловить эту поломку. При смене tally_ChestWithFoodAndIron_CountsMatch проверь это утверждение.");
        assertEquals(
                3,
                lots.iron(),
                "Три железных слитка видны в железе: иначе добыча не видит запас и зовёт экспедицию зря"
                        + " При смене tally_ChestWithFoodAndIron_CountsMatch проверь это утверждение.");
        assertEquals(
                1,
                lots.weapons(),
                "Железный меч — одно оружие: иначе оценка сил шлёт безоружных за оружием, которого нет"
                        + " При смене tally_ChestWithFoodAndIron_CountsMatch проверь это утверждение.");
    }

    @Test
    void tally_Seeds_CountsAsSeeds() {
        StockLots lots =
                new StockCatalog(TABLE).tally(Map.of("minecraft:wheat_seeds", 7, "minecraft:beetroot_seeds", 3));

        assertEquals(
                10,
                lots.seeds(),
                "Семена пшеницы и свёклы считаются семенами: снимок работы фермы видит запас для посадки"
                        + " Иначе тест tally_Seeds_CountsAsSeeds перестанет ловить эту поломку. При смене tally_Seeds_CountsAsSeeds проверь это утверждение.");
    }

    @Test
    void tally_EmptyMap_Zeroes() {
        StockLots lots = new StockCatalog(TABLE).tally(Map.of());

        assertEquals(
                0,
                lots.foodItems(),
                "Пустые сундуки — ноль еды: иначе сытое поселение голодает на бумаге"
                        + " При смене tally_EmptyMap_Zeroes проверь это утверждение.");
        assertEquals(
                0,
                lots.ore(),
                "Пустые сундуки — ноль руды: иначе шахта не зовётся, когда она нужна"
                        + " При смене tally_EmptyMap_Zeroes проверь это утверждение.");
        assertEquals(
                0,
                lots.weapons(),
                "Пустые сундуки — ноль оружия: иначе безоружные духи идут в бой голыми руками"
                        + " При смене tally_EmptyMap_Zeroes проверь это утверждение.");
    }

    @Test
    void tally_UnknownItem_DoesNotBreak() {
        StockLots lots = new StockCatalog(TABLE).tally(Map.of("minecraft:netherite_scrap", 2));

        assertEquals(
                0,
                lots.foodItems(),
                "Неизвестный предмет не ломает счёт: иначе мод с новой едой роняет тик интеллекта"
                        + " При смене tally_UnknownItem_DoesNotBreak проверь это утверждение.");
        assertEquals(
                0,
                lots.ore(),
                "Неизвестный предмет не считается рудой: иначе чужой лут глушит спрос добычи"
                        + " При смене tally_UnknownItem_DoesNotBreak проверь это утверждение.");
    }

    @Test
    void tally_RawIron_CountsAsOreAndIron() {
        StockLots lots = new StockCatalog(TABLE).tally(Map.of("minecraft:raw_iron", 4));

        assertEquals(
                4,
                lots.ore(),
                "Необожжённое железо — руда: шахта видит добычу до плавки"
                        + " Иначе тест tally_RawIron_CountsAsOreAndIron перестанет ловить эту поломку. При смене tally_RawIron_CountsAsOreAndIron проверь это утверждение.");
        assertEquals(
                4,
                lots.iron(),
                "Необожжённое железо — железо: переплавка видит сырьё"
                        + " Иначе тест tally_RawIron_CountsAsOreAndIron перестанет ловить эту поломку. При смене tally_RawIron_CountsAsOreAndIron проверь это утверждение.");
    }

    @Test
    void tally_CoalAndCharcoal_BothFuel() {
        StockLots lots = new StockCatalog(TABLE).tally(Map.of("minecraft:coal", 7, "minecraft:charcoal", 2));

        assertEquals(
                9,
                lots.processing().charcoal(),
                "Обычный уголь — то же топливо: при 7 угле древесный не пережигают ради факелов (INC-33)"
                        + " Иначе тест tally_CoalAndCharcoal_BothFuel перестанет ловить эту поломку. При смене tally_CoalAndCharcoal_BothFuel проверь это утверждение.");
    }

    @Test
    void tally_ProcessingItems_CountedSeparately() {
        StockLots lots = new StockCatalog(TABLE)
                .tally(Map.of(
                        "minecraft:charcoal",
                        5,
                        "minecraft:oak_planks",
                        8,
                        "minecraft:stick",
                        4,
                        "minecraft:beef",
                        3,
                        "minecraft:oak_log",
                        9));

        assertEquals(
                5,
                lots.processing().charcoal(),
                "Уголь считается отдельно от руды: иначе спрос плавки смотрит на железо вместо топлива"
                        + " При смене tally_ProcessingItems_CountedSeparately проверь это утверждение.");
        assertEquals(
                8,
                lots.processing().planks(),
                "Доски считаются по суффиксу _planks: иначе крафт не видит запас"
                        + " При смене tally_ProcessingItems_CountedSeparately проверь это утверждение.");
        assertEquals(
                4,
                lots.processing().sticks(),
                "Палки считаются отдельно: по ним спрос на палки"
                        + " Иначе тест tally_ProcessingItems_CountedSeparately перестанет ловить эту поломку. При смене tally_ProcessingItems_CountedSeparately проверь это утверждение.");
        assertEquals(
                3,
                lots.processing().rawFoods().get("minecraft:beef"),
                "Сырая говядина попадает в опись сырья: иначе готовка не знает, что жарить"
                        + " При смене tally_ProcessingItems_CountedSeparately проверь это утверждение.");
        assertEquals(
                9,
                lots.processing().logs().get("minecraft:oak_log"),
                "Брёвна попадают в опись пород: иначе доски не сделать из имеющегося дерева"
                        + " При смене tally_ProcessingItems_CountedSeparately проверь это утверждение.");
    }

    @Test
    void tally_CookedFood_AddsCookedNutrition() {
        NutritionTable cooked = new NutritionTable(Map.of("minecraft:cooked_beef", 8.0));
        StockLots lots = new StockCatalog(cooked).tally(Map.of("minecraft:cooked_beef", 2));

        assertEquals(
                16.0,
                lots.processing().cookedNutrition(),
                "Сытность готовой еды считается отдельно от всей еды: по ней видно, хватает ли приготовленного"
                        + " Иначе тест tally_CookedFood_AddsCookedNutrition перестанет ловить эту поломку. При смене tally_CookedFood_AddsCookedNutrition проверь это утверждение.");
    }

    @Test
    void tally_NullAndNegative_Throw() {
        StockCatalog catalog = new StockCatalog(TABLE);

        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.tally(null),
                "Опись null не подсчитать: пустой набор задают пустой картой, иначе NPE в тике"
                        + " При смене tally_NullAndNegative_Throw проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.tally(Map.of("minecraft:bread", -1)),
                "Отрицательный счёт ломает спрос: запасы не бывают меньше нуля"
                        + " При смене tally_NullAndNegative_Throw проверь это утверждение.");
        Map<String, Integer> blank = new HashMap<>();
        blank.put(" ", 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.tally(blank),
                "Предмет без имени не опознать: счёт идёт по id предметов"
                        + " Иначе тест tally_NullAndNegative_Throw перестанет ловить эту поломку. При смене tally_NullAndNegative_Throw проверь это утверждение.");
    }

    @Test
    void catalog_NullTable_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new StockCatalog(null),
                "Подсчёт без таблицы сытности не идёт: еда ранжируется по данным"
                        + " Иначе тест catalog_NullTable_Throws перестанет ловить эту поломку. При смене catalog_NullTable_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("сытности"),
                "Сообщение называет причину: при падении видно, что нет данных о еде"
                        + " Иначе тест catalog_NullTable_Throws перестанет ловить эту поломку. При смене catalog_NullTable_Throws проверь это утверждение.");
    }

    @Test
    void defaultTable_KnownFoods_HasExpectedNutrition() {
        NutritionTable table = StockCatalog.defaultTable();

        assertTrue(
                table.nutrition().get("minecraft:bread") == 5.0,
                "Хлеб в таблице по умолчанию с сытностью 5: иначе сундук с хлебом не считается едой"
                        + " При смене defaultTable_KnownFoods_HasExpectedNutrition проверь это утверждение.");
        assertTrue(
                table.nutrition().get("minecraft:cooked_beef") == 8.0,
                "Жареная говядина сытнее хлеба: что сытнее — то приоритетнее в норме на душу"
                        + " Иначе тест defaultTable_KnownFoods_HasExpectedNutrition перестанет ловить эту поломку. При смене defaultTable_KnownFoods_HasExpectedNutrition проверь это утверждение.");
    }
}
