package com.theminespirit.core.board;

/**
 * Виды заданий доски. Набор фиксированный: каждый кейс имеет приоритет, гейт и план
 * по своему имени, поэтому новый кейс добавляется только вместе с ними.
 * Константы объявлены по алфавиту: обход {@link #values()} обязан быть упорядочен
 * по имени константы (детерминизм набора на любой JVM).
 */
public enum BoardCase {
    BREED_ANIMALS,
    BUILD_FENCE,
    BUILD_HOUSE,
    BUILD_MINE,
    BUILD_PEN,
    CAPTURE_ANIMAL,
    CLEAR_GROUNDS,
    COLLECT_DROPS,
    CRAFTING_STATION,
    CRAFT_ANVIL,
    CRAFT_BED,
    CRAFT_SHEARS,
    CRAFT_SMITHING_TABLE,
    CRAFT_SMOKER,
    CRAFT_STONE_AXE,
    CRAFT_STONE_PICKAXE,
    CRAFT_STONE_SHOVEL,
    CRAFT_STONE_SWORD,
    CRAFT_WOODEN_AXE,
    CRAFT_WOODEN_PICKAXE,
    CRAFT_WOODEN_SHOVEL,
    DISMANTLE_SHELTER,
    EAT_TO_FULL,
    ENCHANT_ROOM,
    ENCHANT_SHELVES,
    FILL_CRATER,
    FISH,
    GATHER_MATERIALS,
    HERD_CULL,
    HUNT_WILD_SHEEP,
    LIGHTING,
    LURE_ANIMAL,
    RELOCATE_BUILDING,
    REPAIR_BUILDING,
    REPAIR_FENCE,
    REPAIR_TOOL,
    RESTOCK_FOOD,
    SHEAR_SHEEP,
    STOCK_CHESTS,
    SUPPLY_SITE,
    TRANSFER_ITEM,
    WEAVE_WOOL
}
