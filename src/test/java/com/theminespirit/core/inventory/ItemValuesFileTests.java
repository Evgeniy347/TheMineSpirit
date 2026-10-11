package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ItemValuesFileTests {
    private static final ValueTable VALUES = ValueTable.load();

    @Test
    void itemValuesFile_Loaded_ParsesWithoutError() {
        assertTrue(VALUES.rules().size() >= 25,
                "Файл data/theminespirit/inventory/item_values.json обязан описывать не меньше 25 правил: "
                        + "ценность предметов решает, что дух берёт при переполнении инвентаря и что оставляет "
                        + "в базовом наборе, десять строк закрыли бы только слитки");
    }

    @Test
    void itemValuesFile_Rules_CarryValuesBelowLimit() {
        for (ValueRule rule : VALUES.rules()) {
            assertTrue(rule.value() < ValueTable.VALUE_LIMIT,
                    "Ценность правила " + rule.match() + " равна " + rule.value() + " и не ниже предела "
                            + ValueTable.VALUE_LIMIT + ": цена выше предела выбивает шкалу, и сравнение стопок "
                            + "перестаёт отличать 'дорого' от 'очень дорого'");
        }
        assertTrue(VALUES.defaultValue() < ValueTable.VALUE_LIMIT,
                "Значение default=" + VALUES.defaultValue() + " тоже ниже предела " + ValueTable.VALUE_LIMIT
                        + ": default получают все неописанные предметы");
    }

    @Test
    void itemValuesFile_Categories_CoverEveryItemOfFile() {
        for (String item : new String[] {"iron_pickaxe", "diamond_chestplate", "bread", "iron_ingot", "oak_planks",
                "cobblestone", "bucket", "create:andesite_alloy", "stick", "ancient_debris"}) {
            String category = VALUES.categoryOf(item);
            assertTrue(!category.isBlank(),
                    "Предмет '" + item + "' обязан попасть в категорию файла ценностей: сортёр сундука "
                            + "расставляет стопки по категориям, предмет без категории сломал бы разгрузку");
        }
    }
}
