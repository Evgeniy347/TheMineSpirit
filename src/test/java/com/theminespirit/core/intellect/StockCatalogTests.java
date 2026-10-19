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
                "Сундук с 5 хлебом даёт 5 еды: иначе спрос фермы не видит запас и гонит духов в поле зря");
        assertEquals(
                25.0,
                lots.foodNutrition(),
                "Сытность 5 хлебов по 5 — 25: спрос фермы считается от сытности, а не от штук");
        assertEquals(
                3,
                lots.iron(),
                "Три железных слитка видны в железе: иначе добыча не видит запас и зовёт экспедицию зря");
        assertEquals(
                1,
                lots.weapons(),
                "Железный меч — одно оружие: иначе оценка сил шлёт безоружных за оружием, которого нет");
    }

    @Test
    void tally_Seeds_CountsAsSeeds() {
        StockLots lots =
                new StockCatalog(TABLE).tally(Map.of("minecraft:wheat_seeds", 7, "minecraft:beetroot_seeds", 3));

        assertEquals(
                10,
                lots.seeds(),
                "Семена пшеницы и свёклы считаются семенами: снимок работы фермы видит запас для посадки");
    }

    @Test
    void tally_EmptyMap_Zeroes() {
        StockLots lots = new StockCatalog(TABLE).tally(Map.of());

        assertEquals(0, lots.foodItems(), "Пустые сундуки — ноль еды: иначе сытое поселение голодает на бумаге");
        assertEquals(0, lots.ore(), "Пустые сундуки — ноль руды: иначе шахта не зовётся, когда она нужна");
        assertEquals(0, lots.weapons(), "Пустые сундуки — ноль оружия: иначе безоружные духи идут в бой голыми руками");
    }

    @Test
    void tally_UnknownItem_DoesNotBreak() {
        StockLots lots = new StockCatalog(TABLE).tally(Map.of("minecraft:netherite_scrap", 2));

        assertEquals(
                0,
                lots.foodItems(),
                "Неизвестный предмет не ломает счёт: иначе мод с новой едой роняет тик интеллекта");
        assertEquals(0, lots.ore(), "Неизвестный предмет не считается рудой: иначе чужой лут глушит спрос добычи");
    }

    @Test
    void tally_RawIron_CountsAsOreAndIron() {
        StockLots lots = new StockCatalog(TABLE).tally(Map.of("minecraft:raw_iron", 4));

        assertEquals(4, lots.ore(), "Необожжённое железо — руда: шахта видит добычу до плавки");
        assertEquals(4, lots.iron(), "Необожжённое железо — железо: переплавка видит сырьё");
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
                "Уголь считается отдельно от руды: иначе спрос плавки смотрит на железо вместо топлива");
        assertEquals(8, lots.processing().planks(), "Доски считаются по суффиксу _planks: иначе крафт не видит запас");
        assertEquals(4, lots.processing().sticks(), "Палки считаются отдельно: по ним спрос на палки");
        assertEquals(
                3,
                lots.processing().rawFoods().get("minecraft:beef"),
                "Сырая говядина попадает в опись сырья: иначе готовка не знает, что жарить");
        assertEquals(
                9,
                lots.processing().logs().get("minecraft:oak_log"),
                "Брёвна попадают в опись пород: иначе доски не сделать из имеющегося дерева");
    }

    @Test
    void tally_CookedFood_AddsCookedNutrition() {
        NutritionTable cooked = new NutritionTable(Map.of("minecraft:cooked_beef", 8.0));
        StockLots lots = new StockCatalog(cooked).tally(Map.of("minecraft:cooked_beef", 2));

        assertEquals(
                16.0,
                lots.processing().cookedNutrition(),
                "Сытность готовой еды считается отдельно от всей еды: по ней видно, хватает ли приготовленного");
    }

    @Test
    void tally_NullAndNegative_Throw() {
        StockCatalog catalog = new StockCatalog(TABLE);

        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.tally(null),
                "Опись null не подсчитать: пустой набор задают пустой картой, иначе NPE в тике");
        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.tally(Map.of("minecraft:bread", -1)),
                "Отрицательный счёт ломает спрос: запасы не бывают меньше нуля");
        Map<String, Integer> blank = new HashMap<>();
        blank.put(" ", 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.tally(blank),
                "Предмет без имени не опознать: счёт идёт по id предметов");
    }

    @Test
    void catalog_NullTable_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new StockCatalog(null),
                "Подсчёт без таблицы сытности не идёт: еда ранжируется по данным");

        assertTrue(
                error.getMessage().contains("сытности"),
                "Сообщение называет причину: при падении видно, что нет данных о еде");
    }

    @Test
    void defaultTable_KnownFoods_HasExpectedNutrition() {
        NutritionTable table = StockCatalog.defaultTable();

        assertTrue(
                table.nutrition().get("minecraft:bread") == 5.0,
                "Хлеб в таблице по умолчанию с сытностью 5: иначе сундук с хлебом не считается едой");
        assertTrue(
                table.nutrition().get("minecraft:cooked_beef") == 8.0,
                "Жареная говядина сытнее хлеба: что сытнее — то приоритетнее в норме на душу");
    }
}
