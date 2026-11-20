package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ItemValuesFileTests {
    private static final ValueTable VALUES = ValueTable.load();

    @Test
    void itemValuesFile_Loaded_ParsesWithoutError() {
        assertTrue(
                VALUES.rules().size() >= 25,
                "Файл data/theminespirit/inventory/item_values.json обязан описывать не меньше 25 правил: "
                        + "ценность предметов решает, что дух берёт при переполнении инвентаря и что оставляет "
                        + "в базовом наборе, десять строк закрыли бы только слитки, иначе ценность почти всех "
                        + "предметов нулевая. При смене файла проверь число правил");
    }

    @Test
    void itemValuesFile_Rules_CarryValuesBelowLimit() {
        for (ValueRule rule : VALUES.rules()) {
            assertTrue(
                    rule.value() < ValueTable.VALUE_LIMIT,
                    "Ценность правила " + rule.match() + " равна " + rule.value() + " и не ниже предела "
                            + ValueTable.VALUE_LIMIT + ": цена выше предела выбивает шкалу, и сравнение стопок "
                            + "перестаёт отличать 'дорого' от 'очень дорого'. При смене файла проверь каждое правило против предела");
        }
        assertTrue(
                VALUES.defaultValue() < ValueTable.VALUE_LIMIT,
                "Значение default=" + VALUES.defaultValue() + " тоже ниже предела " + ValueTable.VALUE_LIMIT
                        + ": default получают все неописанные предметы, и без ограничения шкала врёт, иначе "
                        + "сравнение стопок ломается. При смене файла проверь defaultValue");
    }

    @Test
    void itemValuesFile_Categories_CoverEveryItemOfFile() {
        for (String item : new String[] {
            "iron_pickaxe",
            "diamond_chestplate",
            "bread",
            "iron_ingot",
            "oak_planks",
            "cobblestone",
            "bucket",
            "create:andesite_alloy",
            "stick",
            "ancient_debris"
        }) {
            String category = VALUES.categoryOf(item);
            assertTrue(
                    !category.isBlank(),
                    "Предмет '" + item + "' обязан попасть в категорию файла ценностей: сортёр сундука "
                            + "расставляет стопки по категориям, иначе предмет без категории сломал бы разгрузку. "
                            + "При смене таблицы проверь каждый предмет из списка");
        }
    }

    @ParameterizedTest(name = "{0} стоит не меньше 100")
    @CsvSource({
        "netherite_scrap",
        "netherite_ingot",
        "ancient_debris",
        "netherite_block",
        "diamond",
        "deepslate_diamond_ore",
        "emerald",
        "gold_ingot",
        "iron_block",
        "gold_block",
        "diamond_block",
        "emerald_block",
        "coal_block",
        "redstone_block",
        "lapis_block",
        "copper_block",
        "nether_star",
        "enchanted_golden_apple",
        "golden_apple",
        "totem_of_undying",
        "elytra",
        "trident",
        "enchanted_book",
        "heart_of_the_sea",
        "beacon",
        "netherite_upgrade_smithing_template",
        "raw_iron_block",
        "raw_gold_block",
        "raw_copper_block",
        "white_shulker_box",
        "shulker_box",
        "diamond_chestplate",
        "netherite_helmet"
    })
    void itemValuesFile_ExpensiveItems_CostAtLeastHundred(String item) {
        assertTrue(
                VALUES.valueOf(item) >= 100,
                "Предмет '" + item + "' стоит " + VALUES.valueOf(item) + ", а должен не меньше 100: "
                        + "цена ниже сотни делает его мусором, и при переполнении дух выбросит железные "
                        + "блоки, шалкер или тотем ради горсти хлеба. При смене цен проверь полный список дорогих предметов");
    }

    @ParameterizedTest(name = "{0} дороже девяти {1}")
    @CsvSource({
        "iron_block, iron_ingot",
        "gold_block, gold_ingot",
        "copper_block, copper_ingot",
        "redstone_block, redstone",
        "lapis_block, lapis_lazuli",
        "coal_block, coal",
        "diamond_block, diamond",
        "emerald_block, emerald",
        "netherite_block, netherite_ingot",
        "raw_iron_block, raw_iron",
        "raw_gold_block, raw_gold",
        "raw_copper_block, raw_copper"
    })
    void itemValuesFile_StorageBlock_CostsAtLeastNineOfItsIngot(String block, String ingot) {
        int blockValue = VALUES.valueOf(block);
        int ingotValue = VALUES.valueOf(ingot);

        assertTrue(
                blockValue >= 9 * ingotValue,
                "Блок хранения '" + block + "' стоит " + blockValue + ", а девять '" + ingot + "' стоят "
                        + (9 * ingotValue) + ": иначе при дешевизне блока дух выбросит блок "
                        + "и оставит девять предметов, потеряв место в сундуке. При смене цен проверь пары блок-слиток");
    }

    @Test
    void itemValuesFile_IngotAndGoldenApple_CostMoreThanTheirCheaperForm() {
        assertTrue(
                VALUES.valueOf("iron_ingot") > VALUES.valueOf("iron_nugget"),
                "Слиток дороже самородка: иначе сравнение стопок предпочтёт девять обломков слитку "
                        + "и сломает и подбор, и базовый набор. При смене цен проверь слиток против самородка");
        assertTrue(
                VALUES.valueOf("golden_apple") > VALUES.valueOf("apple"),
                "Золотое яблоко дороже обычного: «остальная еда по 4» не должна делать лечебное яблоко "
                        + "дешёвой морковью, иначе лечебное яблоко выбросят. При смене цен проверь золотое яблоко против обычного");
    }
}
