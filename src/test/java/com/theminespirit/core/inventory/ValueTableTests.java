package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ValueTableTests {
    private static final String ORIGIN = "data/theminespirit/inventory/item_values.json";

    private static final ValueTable VALUES = ValueTable.load();

    @ParameterizedTest(name = "{0} стоит {1}")
    @CsvSource({
        "wooden_pickaxe, 5",
        "stone_sword, 10",
        "golden_shovel, 40",
        "iron_pickaxe, 80",
        "diamond_axe, 400",
        "netherite_hoe, 900",
        "leather_boots, 5",
        "chainmail_helmet, 25",
        "iron_chestplate, 80",
        "netherite_leggings, 1200",
        "iron_ingot, 40",
        "iron_nugget, 4",
        "raw_iron, 30",
        "deepslate_iron_ore, 30",
        "gold_ingot, 100",
        "raw_gold, 60",
        "nether_gold_ore, 60",
        "netherite_scrap, 250",
        "ancient_debris, 1000",
        "diamond, 500",
        "deepslate_diamond_ore, 500",
        "emerald, 300",
        "deepslate_emerald_ore, 300",
        "copper_ingot, 12",
        "redstone, 6",
        "lapis_lazuli, 6",
        "coal, 8",
        "oak_log, 4",
        "oak_planks, 1",
        "stick, 1",
        "bucket, 4",
        "water_bucket, 4",
        "cooked_beef, 10",
        "bread, 8",
        "apple, 4",
        "torch, 2",
        "cobblestone, 0",
        "sand, 0",
        "netherrack, 0",
        "minecraft:netherite_block, 9000",
        "netherite_ingot, 1000",
        "golden_apple, 120",
        "elytra, 500",
        "totem_of_undying, 500",
        "nether_star, 2000",
        "trident, 500",
        "enchanted_book, 500",
        "heart_of_the_sea, 500",
        "beacon, 500",
        "netherite_upgrade_smithing_template, 500",
        "white_shulker_box, 500",
        "shulker_box, 500",
        "iron_block, 360",
        "gold_block, 900",
        "diamond_block, 4500",
        "emerald_block, 2700",
        "redstone_block, 120",
        "coal_block, 120",
        "copper_block, 108",
        "lapis_block, 120",
        "enchanted_golden_apple, 2000"
    })
    void valueOf_ItemId_ReturnsRuleValue(String item, int expected) {
        assertEquals(
                expected,
                VALUES.valueOf(item),
                "Предмет '" + item + "' обязан стоить " + expected + ": таблица ценностей решает, что дух берёт "
                        + "при переполнении инвентаря и что оставляет в базовом наборе. Ожидание задаёт "
                        + "data/theminespirit/inventory/item_values.json");
    }

    @Test
    void valueOf_ModdedItem_KeepsDefaultValue() {
        assertEquals(
                1,
                VALUES.valueOf("create:copper_pickaxe"),
                "Шаблон без ':' действует только в пространстве minecraft: предмет чужого мода получает default=1 "
                        + "и выглядит для духа недорогим, пока его не опишут в item_values.json");
    }

    @Test
    void valueOf_UnknownItem_TakesDefaultFromFile() {
        assertEquals(
                1,
                VALUES.valueOf("create:andesite_alloy"),
                "Предмет вне всех правил получает default=1: нулевая цена сделала бы его самым выгодным на выброс, "
                        + "а завышенная — предметом, который дух тащит вместо алмаза");
    }

    @Test
    void valueOf_OverlappingRules_FirstMatchWins() {
        ValueTable table = ValueTable.parse("""
                {"default": 0,
                 "rules": [{"match": ["*_ingot"], "value": 5},
                           {"match": ["iron_ingot", "copper_block"], "value": 40}],
                 "categories": [{"name": "misc", "match": ["*"]}]}
                """, ORIGIN);

        assertEquals(
                5,
                table.valueOf("iron_ingot"),
                "Правило, стоящее выше, забирает предмет себе: именно порядок правил делает исключения вроде "
                        + "netherite_scrap рабочими, поэтому перестановка строк меняет цену предмета");
        assertEquals(
                40,
                table.valueOf("copper_block"),
                "Правило ниже всё ещё работает для остальных своих шаблонов: иначе оно стало бы мёртвым "
                        + "и его цена отличилась бы от задуманной");
    }

    @ParameterizedTest(name = "{0} из группы {1}")
    @CsvSource({
        "iron_pickaxe, tools",
        "bow, tools",
        "diamond_chestplate, armor",
        "elytra, armor",
        "bread, food",
        "cooked_cod, food",
        "iron_ingot, materials",
        "raw_iron, materials",
        "oak_planks, blocks",
        "crafting_table, blocks",
        "torch, blocks",
        "bucket, misc",
        "music_disc_cat, misc"
    })
    void categoryOf_ItemId_ReturnsCategoryName(String item, String expected) {
        assertEquals(
                expected,
                VALUES.categoryOf(item),
                "Категория предмета '" + item + "' обязана быть '" + expected + "': по категориям сортёр сундука "
                        + "расставляет стопки, и предмет в чужой группе разъезжается по сундуку при каждой разгрузке");
    }

    @Test
    void stackValue_MultipleItems_MultipliesUnitValueByCount() {
        assertEquals(
                0,
                VALUES.stackValue(new Stack("cobblestone", 0, 64, 64)),
                "Стопка булыжника ничего не стоит: при переполнении инвентаря дух выбросит именно её");
        assertEquals(
                1500,
                VALUES.stackValue(new Stack("diamond", 3, 3, 64)),
                "Три алмаза стоят втрое дороже одного: сравнение стопок, а не предметов, решает, что дороже");
    }

    @Test
    void parse_BrokenJson_ThrowsWithPathAndReason() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ValueTable.parse("{\"default\": 1, ", ORIGIN),
                "Битый файл ценностей обязан ронять загрузку: тихое «все предметы по рублю» сделало бы выбор "
                        + "при переполнении инвентаря случайным");

        assertTrue(
                error.getMessage().contains(ORIGIN),
                "Сообщение об ошибке обязано называть путь к файлу: по нему автор находит, какой файл править. "
                        + "Фактическое: " + error.getMessage());
        assertTrue(
                error.getMessage().contains("JSON"),
                "Сообщение об ошибке обязано называть причину — неразобранный JSON. Фактическое: "
                        + error.getMessage());
    }

    @Test
    void parse_UnknownField_ThrowsWithFieldName() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ValueTable.parse("""
                        {"default": 1, "valu": 5,
                         "rules": [{"match": ["stone"], "value": 1}],
                         "categories": [{"name": "misc", "match": ["*"]}]}
                        """, ORIGIN),
                "Опечатка в имени поля обязана быть ошибкой: правило с полем valu не читается молча, "
                        + "а тихий пропуск сделал бы таблицу неполной");

        assertTrue(
                error.getMessage().contains("valu"),
                "Сообщение обязано называть неизвестное поле 'valu': по нему правят файл. Фактическое: "
                        + error.getMessage());
    }

    @Test
    void parse_PatternOutsideFilterAlphabet_ThrowsWithPattern() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ValueTable.parse("""
                        {"default": 1,
                         "rules": [{"match": ["Iron_Ingot"], "value": 1}],
                         "categories": [{"name": "misc", "match": ["*"]}]}
                        """, ORIGIN),
                "Шаблон вне алфавита IdFilter обязан быть ошибкой: такой шаблон не совпадёт ни с одним id "
                        + "и тихо выключит правило");

        assertTrue(
                error.getMessage().contains("Iron_Ingot"),
                "Сообщение обязано называть плохой шаблон 'Iron_Ingot': по нему правят файл. Фактическое: "
                        + error.getMessage());
    }

    @Test
    void parse_NegativeValue_ThrowsWithValue() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ValueTable.parse("""
                        {"default": 1,
                         "rules": [{"match": ["*_ingot"], "value": -5}],
                         "categories": [{"name": "misc", "match": ["*"]}]}
                        """, ORIGIN),
                "Отрицательная ценность обязана быть ошибкой: «самое дешёвое» с отрицательными ценами "
                        + "выбирает предмет, которого нет в инвентаре");

        assertTrue(
                error.getMessage().contains("-5"),
                "Сообщение обязано называть отрицательное значение -5: по нему находят строку файла. " + "Фактическое: "
                        + error.getMessage());
    }

    @Test
    void parse_DeadRule_ThrowsWithRuleNumber() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ValueTable.parse("""
                        {"default": 1,
                         "rules": [{"match": ["*_ingot"], "value": 5},
                                   {"match": ["iron_ingot"], "value": 40}],
                         "categories": [{"name": "misc", "match": ["*"]}]}
                        """, ORIGIN),
                "Правило, перекрытое предыдущим целиком, никогда не сработает: оно обязано быть ошибкой файла, "
                        + "иначе цена предмета в таблице расходится с ценой в правилах выше");

        assertTrue(
                error.getMessage().contains("№1"),
                "Сообщение обязано называть мёртвое правило №1: по номеру находят строку файла. Фактическое: "
                        + error.getMessage());
    }

    @Test
    void parse_EmptyRules_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ValueTable.parse("""
                        {"default": 1, "rules": [],
                         "categories": [{"name": "misc", "match": ["*"]}]}
                        """, ORIGIN),
                "Файл без правил обязан быть ошибкой: иначе все предметы получают default и таблица теряет смысл");

        assertTrue(
                error.getMessage().contains("rules"),
                "Сообщение обязано называть отсутствующий список rules. Фактическое: " + error.getMessage());
    }

    @Test
    void parse_LastCategoryWithoutCatchAll_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ValueTable.parse("""
                        {"default": 1,
                         "rules": [{"match": ["*_ingot"], "value": 1}],
                         "categories": [{"name": "materials", "match": ["*_ingot"]}]}
                        """, ORIGIN),
                "Последняя категория без шаблона '*' обязана быть ошибкой: предмет вне категорий не получил бы "
                        + "места при сортировке сундука, и сортёр упал бы уже в игре");

        assertTrue(
                error.getMessage().contains("materials"),
                "Сообщение обязано называть последнюю категорию 'materials': по имени правят файл. Фактическое: "
                        + error.getMessage());
    }

    @Test
    void load_MissingFile_ThrowsWithPath() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ValueTable.load("data/theminespirit/inventory/absent.json"),
                "Отсутствие файла ценностей на classpath обязано быть ошибкой: без таблицы дух не решит, "
                        + "что дороже при переполнении инвентаря");

        assertTrue(
                error.getMessage().contains("data/theminespirit/inventory/absent.json"),
                "Сообщение обязано называть путь, по которому файл искали: по нему находят потерянный ресурс. "
                        + "Фактическое: " + error.getMessage());
    }

    @ParameterizedTest(name = "файл содержит правило для {0}")
    @ValueSource(
            strings = {
                "netherite_scrap",
                "iron_pickaxe",
                "iron_helmet",
                "diamond",
                "gold_ingot",
                "raw_iron",
                "copper_ingot",
                "redstone",
                "coal",
                "oak_log",
                "cooked_beef",
                "bread",
                "torch",
                "cobblestone"
            })
    void loadedTable_RuleCoversVanillaItem(String item) {
        assertTrue(
                VALUES.rules().stream().anyMatch(rule -> rule.covers(item)),
                "Предмет '" + item + "' обязан быть описан правилом файла ценностей: незакрытый предмет получает "
                        + "default и выглядит для духа таким же ценным, как неизвестный мусор");
    }

    @Test
    void loadedTable_Categories_FollowSortingOrder() {
        List<String> names = VALUES.categories().stream().map(Category::name).toList();

        assertEquals(
                List.of("tools", "armor", "food", "materials", "blocks", "misc"),
                names,
                "Порядок категорий в файле задаёт раскладку сундука: инструменты и оружие, броня, еда, "
                        + "руды и слитки, блоки, прочее. Перестановка меняет порядок стопок в сундуке");
    }
}
