package com.theminespirit.core.board;

/**
 * Виды заданий доски. Набор фиксированный: каждый кейс имеет приоритет, гейт и план
 * по своему имени, поэтому новый кейс добавляется только вместе с ними.
 * Константы объявлены по алфавиту: обход {@link #values()} обязан быть упорядочен
 * по имени константы (детерминизм набора на любой JVM).
 */
public enum BoardCase {
    BUILD_FENCE,
    BUILD_HOUSE,
    BUILD_MINE,
    BUILD_PEN,
    CAPTURE_ANIMAL,
    CLEAR_GROUNDS,
    CRAFTING_STATION,
    CRAFT_TOOLS,
    DISMANTLE_SHELTER,
    ENCHANT_ROOM,
    ENCHANT_SHELVES,
    FILL_CRATER,
    LIGHTING,
    LURE_ANIMAL,
    REPAIR_BUILDING,
    REPAIR_FENCE,
    STOCK_CHESTS,
    SUPPLY_SITE
}
