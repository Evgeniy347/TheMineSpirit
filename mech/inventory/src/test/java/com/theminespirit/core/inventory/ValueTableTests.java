package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                        + "при переполнении инвентаря и что оставляет, иначе выбор дропа врёт. Ожидание задаёт "
                        + "data/theminespirit/inventory/item_values.json. При смене таблицы проверь цену " + item);
    }

    @Test
    void valueOf_ModdedItem_KeepsDefaultValue() {
        assertEquals(
                1,
                VALUES.valueOf("create:copper_pickaxe"),
                "Шаблон без ':' действует только в пространстве minecraft: иначе предмет чужого мода получает "
                        + "default=1 и выглядит для духа недорогим. При смене таблицы проверь modded-предмет");
    }

    @Test
    void valueOf_UnknownItem_TakesDefaultFromFile() {
        assertEquals(
                1,
                VALUES.valueOf("create:andesite_alloy"),
                "Предмет вне всех правил получает default=1: иначе нулевая цена сделала бы его самым выгодным "
                        + "на выброс, а завышенная — предметом, который дух тащит вместо алмаза. "
                        + "При смене таблицы проверь default");
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
                        + "netherite_scrap рабочими, иначе перестановка строк меняет цену предмета. "
                        + "При смене table проверь порядок правил");
        assertEquals(
                40,
                table.valueOf("copper_block"),
                "Правило ниже всё ещё работает для остальных своих шаблонов: иначе оно стало бы мёртвым "
                        + "и его цена отличилась бы от задуманной. При смене table проверь нижнее правило");
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
                        + "расставляет стопки, иначе предмет в чужой группе разъезжается по сундуку. "
                        + "При смене категорий проверь " + item);
    }

    @Test
    void stackValue_MultipleItems_MultipliesUnitValueByCount() {
        assertEquals(
                0,
                VALUES.stackValue(new Stack("cobblestone", 0, 64, 64)),
                "Стопка булыжника ничего не стоит: при переполнении инвентаря дух выбросит именно её, "
                        + "иначе мусор останется в инвентаре. При смене stackValue проверь булыжник");
        assertEquals(
                1500,
                VALUES.stackValue(new Stack("diamond", 3, 3, 64)),
                "Три алмаза стоят втрое дороже одного: сравнение стопок, а не предметов, решает, что дороже, "
                        + "иначе ценное выбросят. При смене stackValue проверь три алмаза");
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
                "Предмет '" + item + "' обязан быть описан правилом файла ценностей: иначе незакрытый предмет "
                        + "получает default и выглядит для духа как неизвестный мусор. "
                        + "При смене файла проверь правило для " + item);
    }

    @Test
    void loadedTable_Categories_FollowSortingOrder() {
        List<String> names = VALUES.categories().stream().map(Category::name).toList();

        assertEquals(
                List.of("tools", "armor", "food", "materials", "blocks", "misc"),
                names,
                "Порядок категорий в файле задаёт раскладку сундука: инструменты и оружие, броня, еда, "
                        + "руды и слитки, блоки, прочее, иначе перестановка меняет порядок стопок. "
                        + "При смене файла проверь порядок категорий");
    }
}
