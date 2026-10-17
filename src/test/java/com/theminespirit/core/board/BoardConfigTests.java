package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.reload.DataTables;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardConfigTests {

    private static Map<String, Integer> expectedPriorities() {
        return Map.ofEntries(
                Map.entry("REPAIR_FENCE", 100),
                Map.entry("REPAIR_BUILDING", 90),
                Map.entry("EAT_TO_FULL", 80),
                Map.entry("CAPTURE_ANIMAL", 75),
                Map.entry("RESTOCK_FOOD", 70),
                Map.entry("BUILD_PEN", 65),
                Map.entry("LURE_ANIMAL", 65),
                Map.entry("DISMANTLE_SHELTER", 65),
                Map.entry("RELOCATE_BUILDING", 60),
                Map.entry("BREED_ANIMALS", 55),
                Map.entry("COLLECT_DROPS", 55),
                Map.entry("SHEAR_SHEEP", 55),
                Map.entry("HERD_CULL", 50),
                Map.entry("HUNT_WILD_SHEEP", 45),
                Map.entry("CLEAR_GROUNDS", 50),
                Map.entry("BUILD_FENCE", 50),
                Map.entry("LIGHTING", 50),
                Map.entry("BUILD_HOUSE", 50),
                Map.entry("BUILD_MINE", 50),
                Map.entry("ENCHANT_ROOM", 50),
                Map.entry("GATHER_MATERIALS", 45),
                Map.entry("SUPPLY_SITE", 40),
                Map.entry("TRANSFER_ITEM", 45),
                Map.entry("CRAFT_STONE_AXE", 40),
                Map.entry("CRAFT_STONE_PICKAXE", 40),
                Map.entry("CRAFT_STONE_SHOVEL", 40),
                Map.entry("CRAFT_STONE_SWORD", 40),
                Map.entry("CRAFT_WOODEN_AXE", 40),
                Map.entry("CRAFT_WOODEN_PICKAXE", 40),
                Map.entry("CRAFT_WOODEN_SHOVEL", 40),
                Map.entry("CRAFT_SHEARS", 40),
                Map.entry("CRAFT_BED", 40),
                Map.entry("WEAVE_WOOL", 40),
                Map.entry("CRAFTING_STATION", 40),
                Map.entry("STOCK_CHESTS", 40),
                Map.entry("FILL_CRATER", 35),
                Map.entry("ENCHANT_SHELVES", 30));
    }

    private static Map<String, Integer> expectedModifiers() {
        return Map.of(
                "pen_caught_animal", 15,
                "pen_missing_bait", 10,
                "pen_species_full", -65,
                "lure_bait_ready", 10,
                "relocate_busy", -20);
    }

    @Test
    void defaults_FromClasspath_PrioritiesMatchLiterals() {
        BoardConfig config = DataTables.INSTANCE.board().current();

        assertEquals(expectedPriorities(), config.priorities(),
                "Таблица базовых приоритетов обязана совпадать с литералами слово в слово: "
                        + "лишний ключ в файле без теста и забытый ключ в тесте красят прогон, "
                        + "а молчаливое расхождение пересортирует выдачу. RESTOCK_FOOD — 70 "
                        + "(решение владельца 16.10 дало число только для 48 единиц, порядок "
                        + "«ниже поимки, выше загона» — из таблицы п. 3)");
    }

    private static Map<String, Integer> expectedLevels() {
        return Map.of(
                "BUILD_MINE", 4,
                "BUILD_PEN", 3,
                "ENCHANT_ROOM", 4,
                "BUILD_HOUSE", 4);
    }

    @Test
    void defaults_FromClasspath_LevelsMatchLiterals() {
        BoardConfig config = DataTables.INSTANCE.board().current();

        assertEquals(expectedLevels(), config.levels(),
                "Таблица уровней обязана совпадать с литералами слово в слово: лишний ключ в "
                        + "файле без теста и забытый ключ в тесте красят прогон, а молчаливое "
                        + "расхождение изменило бы число уровней постройки");
        assertEquals(4, config.levels().get("BUILD_MINE"),
                "Предел в тесте, факт из production: у шахты 4 уровня (проход, расширение, "
                        + "ступеньки, рельсы)");
    }

    @Test
    void defaults_FromClasspath_HerdMatchesLiterals() {
        BoardConfig config = DataTables.INSTANCE.board().current();

        assertEquals(Map.of("maxPerSpecies", 4, "minBreed", 2), config.herd(),
                "Таблица стада обязана совпадать с литералами слово в слово: лимит 4 на вид "
                        + "(ответ владельца 16.10) и минимум пара для разведения. Лишний ключ в "
                        + "файле без теста и забытый ключ в тесте красят прогон");
        assertEquals(4, config.herd().get("maxPerSpecies"),
                "Предел в тесте, факт из production: не больше 4 животных каждого вида");
    }

    @Test
    void defaults_FromClasspath_ToolsMatchLiterals() {
        BoardConfig config = DataTables.INSTANCE.board().current();

        assertEquals(Map.of("perSpirit", 1, "minSets", 1), config.tools(),
                "Таблица комплектов инструментов обязана совпадать с литералами: комплект на духа, "
                        + "но не меньше одного. Лишний ключ в файле без теста и забытый ключ в тесте "
                        + "красят прогон");
        assertEquals(1, config.tools().get("perSpirit"),
                "Предел в тесте, факт из production: по умолчанию по одному комплекту на духа");
        assertEquals(3, ToolSet.count(3),
                "Три духа поселения — три комплекта инструментов: число из горячей таблицы board");
        assertEquals(5, ToolSet.count(Map.of("perSpirit", 0, "minSets", 5), 3),
                "Фиксированный набор: perSpirit 0 при minSets 5 даёт 5 комплектов на любое число духов");
    }

    @Test
    void defaults_FromClasspath_WoolMatchesLiterals() {
        BoardConfig config = DataTables.INSTANCE.board().current();

        assertEquals(Map.of(
                        "min_pen_sheep", 4,
                        "wild_sheep_radius", 48,
                        "wild_sheep_per_day", 4,
                        "string_threshold", 8,
                        "string_reserve", 4,
                        "shears_iron", 2,
                        "bed_wool", 3,
                        "bed_planks", 3,
                        "shear_radius", 8),
                config.wool(),
                "Числа шерсти и кроватей обязаны совпадать с литералами слово в слово: гейт отары, "
                        + "радиус и суточный лимит диких овец, порог и запас нитей, цена ножниц и "
                        + "кровати. Лишний ключ в файле без теста и забытый ключ в тесте красят прогон");
        assertEquals(48, config.wool().get("wild_sheep_radius"),
                "Предел в тесте, факт из production: диких овец ищут в радиусе 48 от дома");
    }

    @Test
    void fromJson_UnknownWoolKey_FailsNamingKey() {
        String json = "{\"wool\":{\"min_pen_ships\":4}}";

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BoardConfig.fromJson(json, "board.json"),
                "Опечатка в ключе шерсти обязана падать: молча потерянный ключ оставил бы гейт "
                        + "отары без ведома настройщика");
        assertTrue(failure.getMessage().contains("min_pen_ships"),
                "Сообщение обязано называть неверный ключ: " + failure.getMessage());
    }

    @Test
    void fromJson_UnknownHerdKey_FailsNamingKey() {
        String json = "{\"herd\":{\"maxPerSpecie\":4}}";

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BoardConfig.fromJson(json, "board.json"),
                "Опечатка в ключе стада обязана падать: молча потерянный ключ оставил бы лимит "
                        + "по умолчанию без ведома настройщика");
        assertTrue(failure.getMessage().contains("maxPerSpecie"),
                "Сообщение обязано называть неверный ключ: " + failure.getMessage());
    }

    @Test
    void fromJson_UnknownToolKey_FailsNamingKey() {
        String json = "{\"tools\":{\"perSpirits\":1}}";

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BoardConfig.fromJson(json, "board.json"),
                "Опечатка в ключе таблицы инструментов обязана падать: молча потерянный ключ "
                        + "оставил бы число комплектов по умолчанию без ведома настройщика");
        assertTrue(failure.getMessage().contains("perSpirits"),
                "Сообщение обязано называть неверный ключ: " + failure.getMessage());
    }

    @Test
    void defaults_FromClasspath_ImmovableMatchesLiterals() {
        BoardConfig config = DataTables.INSTANCE.board().current();

        assertEquals(List.of("BUILD_MINE"), config.immovable(),
                "Неподвижные постройки обязаны совпадать с литералами: пока только вход в шахту "
                        + "(ответ владельца 16.10). Лишний кейс в файле без теста и забытый ключ в "
                        + "тесте красят прогон");
        assertTrue(config.immovable().contains("BUILD_MINE"),
                "Вход в шахту неподвижен: его нельзя переносить");
    }

    @Test
    void fromJson_UnknownImmovableCase_FailsNamingCase() {
        String json = "{\"immovable\":[\"BUILD_MINES\"]}";

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BoardConfig.fromJson(json, "board.json"),
                "Опечатка в имени неподвижного кейса обязана падать: молча потерянная строка "
                        + "оставила бы постройку переносимой без ведома настройщика");
        assertTrue(failure.getMessage().contains("BUILD_MINES"),
                "Сообщение обязано называть неверный кейс: " + failure.getMessage());
    }

    @Test
    void defaults_FromClasspath_ProductsMatchLiterals() {
        BoardConfig config = DataTables.INSTANCE.board().current();

        assertEquals(Map.of(
                        "cow", Map.of("minecraft:beef", 1, "minecraft:leather", 1),
                        "sheep", Map.of("minecraft:mutton", 1, "minecraft:white_wool", 1),
                        "pig", Map.of("minecraft:porkchop", 1),
                        "chicken", Map.of("minecraft:chicken", 1, "minecraft:egg", 1)),
                config.products(),
                "Нормы продуктов на душу обязаны совпадать с литералами: мясо/шерсть/яйца по виду. "
                        + "Лишний ключ в файле без теста и забытый ключ в тесте красят прогон");
        assertEquals(1, config.products().get("cow").get("minecraft:leather"),
                "Предел в тесте, факт из production: с коровы — одна шкура на душу");
    }

    @Test
    void fromJson_UnknownProductSpecies_FailsNamingSpecies() {
        String json = "{\"products\":{\"cows\":{\"minecraft:beef\":1}}}";

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BoardConfig.fromJson(json, "board.json"),
                "Опечатка в имени вида продуктов обязана падать: молча потерянная норма оставила бы "
                        + "вид без запаса продуктов");
        assertTrue(failure.getMessage().contains("cows"),
                "Сообщение обязано называть неверный вид: " + failure.getMessage());
    }

    @Test
    void defaults_FromClasspath_ModifiersMatchLiterals() {
        BoardConfig config = DataTables.INSTANCE.board().current();

        assertEquals(expectedModifiers(), config.modifiers(),
                "Таблица модификаторов обязана совпадать с литералами слово в слово: поменянное "
                        + "число в файле без правки теста пересортирует выдачу молча");
        assertEquals(15, config.modifiers().get("pen_caught_animal"),
                "Предел в тесте, факт из production: дельта пойманного животного — 15");
        assertEquals(-65, config.modifiers().get("pen_species_full"),
                "Предел в тесте, факт из production: полный загон гасит BUILD_PEN (65 − 65 = 0)");
    }

    @Test
    void defaults_FromClasspath_PriorityMatchesRules() {
        BoardConfig config = DataTables.INSTANCE.board().current();

        assertEquals(100, config.priorities().get("REPAIR_FENCE"),
                "Приоритет ремонта ограждений в таблице — 100: первый из источника (п. 4), "
                        + "иначе доска отсортирует выдачу не так, как принято в поселении");
        assertEquals(30, config.priorities().get("ENCHANT_SHELVES"),
                "Приоритет полок — 30: самый низкий, иначе чаровальня перебьёт стройки");
        assertTrue(config.priorities().size() >= BoardCase.values().length,
                "Каждый кейс обязан иметь строку в таблице: кейс-сирота роняет сортировку выдачи");
    }

    @Test
    void defaults_FromClasspath_IntervalsMatchTuning() {
        BoardConfig config = DataTables.INSTANCE.board().current();

        assertEquals(100, config.intervals().get("boardIntervalTicks"),
                "Пересборка раз в 100 тиков — образец DEMAND_INTERVAL_TICKS: число живёт в таблице, "
                        + "а не в константе, иначе /reload его не поменяет");
        assertEquals(200, config.intervals().get("herdScanIntervalTicks"),
                "Опрос животных раз в 200 тиков: каждый тик scan_entities грузил бы тик сервера");
    }

    @Test
    void fromJson_UnknownPriorityKey_FailsNamingCase() {
        String json = "{\"priorities\":{\"CLEAR_GROUNXYZ\":50}}";

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BoardConfig.fromJson(json, "board.json"),
                "Опечатка в имени кейса обязана падать: молча потерянная строка превратила бы "
                        + "кейс в сироту, а сирота роняет сортировку выдачи");
        assertTrue(failure.getMessage().contains("CLEAR_GROUNXYZ"),
                "Сообщение обязано называть неверный ключ: " + failure.getMessage());
    }

    @Test
    void fromJson_UnknownModifierKey_FailsNamingKey() {
        String json = "{\"modifiers\":{\"pen_caught_animals\":15}}";

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BoardConfig.fromJson(json, "board.json"),
                "Опечатка в имени модификатора обязана падать: молча потерянная дельта "
                        + "оставила бы задание без повышения, а сирота не провалилась бы молча");
        assertTrue(failure.getMessage().contains("pen_caught_animals"),
                "Сообщение обязано называть неверный ключ: " + failure.getMessage());
    }

    @Test
    void fromJson_UnknownLevelKey_FailsNamingCase() {
        String json = "{\"levels\":{\"BUILD_MINES\":4}}";

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BoardConfig.fromJson(json, "board.json"),
                "Опечатка в имени кейса уровней обязана падать: молча потерянная строка оставила "
                        + "бы постройку без развития, а сирота не провалилась бы молча");
        assertTrue(failure.getMessage().contains("BUILD_MINES"),
                "Сообщение обязано называть неверный ключ: " + failure.getMessage());
    }

    @Test
    void fromJson_UnknownIntervalKey_FailsNamingKey() {
        String json = "{\"intervals\":{\"boardIntervalMillis\":100}}";

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BoardConfig.fromJson(json, "board.json"),
                "Опечатка в имени интервала обязана падать: молча потерянный ключ оставил бы "
                        + "доску на значениях по умолчанию без ведома настройщика");
        assertTrue(failure.getMessage().contains("boardIntervalMillis"),
                "Сообщение обязано называть неверный ключ: " + failure.getMessage());
    }

    @Test
    void fromJson_NegativeValue_FailsNamingField() {
        String json = "{\"priorities\":{\"REPAIR_FENCE\":-5}}";

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BoardConfig.fromJson(json, "board.json"),
                "Отрицательный приоритет обязан падать: сортировка выдачи сравнивает приоритеты "
                        + "числом, отрицательное значение перевернёт порядок ремонта и стройки");
        assertTrue(failure.getMessage().contains("REPAIR_FENCE"),
                "Сообщение обязано называть ключ: " + failure.getMessage());
    }

    @Test
    void fromJson_BrokenJson_FailsNamingOrigin() {
        assertThrows(IllegalStateException.class, () -> BoardConfig.fromJson("{", "data/custom.json"),
                "Битый JSON обязан падать с путём файла: настройщик обязан увидеть, какой файл "
                        + "исправить, а не гадать");
    }

    @Test
    void fromJson_EmptyTables_AreAllowed() {
        BoardConfig config = BoardConfig.fromJson("{}", "board.json");

        assertTrue(config.priorities().isEmpty() && config.modifiers().isEmpty()
                        && config.intervals().isEmpty(),
                "Пустые таблицы допустимы: недостающее добирают значения по умолчанию из "
                        + "BoardRules, BoardPriorities и BoardTuning");
        assertEquals(BoardRules.priorityOf(BoardCase.REPAIR_FENCE), 100,
                "Без строки в таблице работает приоритет по умолчанию: доска не падает на пустом файле");
    }
}
