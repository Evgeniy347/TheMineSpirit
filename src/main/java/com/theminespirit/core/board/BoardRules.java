package com.theminespirit.core.board;

import com.theminespirit.core.reload.DataTables;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class BoardRules {
    private static final Set<BoardCase> BUILDS = EnumSet.of(
            BoardCase.CLEAR_GROUNDS,
            BoardCase.BUILD_FENCE,
            BoardCase.LIGHTING,
            BoardCase.BUILD_HOUSE,
            BoardCase.PLACE_BEEHIVE,
            BoardCase.BUILD_ORCHARD,
            BoardCase.BUILD_STREETS,
            BoardCase.BUILD_MINE,
            BoardCase.BUILD_WAREHOUSE,
            BoardCase.ENCHANT_ROOM);
    private static final Map<BoardCase, Integer> PRIORITIES = buildPriorities();

    private BoardRules() {}

    public static int priorityOf(BoardCase boardCase) {
        if (boardCase == null) {
            throw new IllegalArgumentException("Приоритет кейса не задан: доска не решит, что важнее, без кейса");
        }
        Integer priority = DataTables.INSTANCE.board().current().priorities().get(boardCase.name());
        if (priority != null) {
            return priority;
        }
        Integer fallback = PRIORITIES.get(boardCase);
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalArgumentException("Приоритет кейса не задан: " + boardCase
                + " отсутствует в таблице приоритетов — добавьте строку в BoardRules");
    }

    private static Map<BoardCase, Integer> buildPriorities() {
        Map<BoardCase, Integer> priorities = new EnumMap<>(BoardCase.class);
        priorities.put(BoardCase.REPAIR_FENCE, 100);
        priorities.put(BoardCase.RESCUE_SPIRIT, 100);
        priorities.put(BoardCase.ACTIVITY_COMBAT, 100);
        priorities.put(BoardCase.ACTIVITY_RETREAT, 100);
        priorities.put(BoardCase.ACTIVITY_RETURN, 30);
        priorities.put(BoardCase.ACTIVITY_GUARD, 30);
        priorities.put(BoardCase.ACTIVITY_ARM, 100);
        priorities.put(BoardCase.ACTIVITY_EXTERNAL, 95);
        priorities.put(BoardCase.ACTIVITY_EXPEDITION, 95);
        priorities.put(BoardCase.ACTIVITY_ESCAPE, 95);
        priorities.put(BoardCase.ACTIVITY_DEFEND, 29);
        priorities.put(BoardCase.ACTIVITY_UNLOAD, 25);
        priorities.put(BoardCase.ACTIVITY_CRAFT, 20);
        priorities.put(BoardCase.ACTIVITY_SMELT, 20);
        priorities.put(BoardCase.ACTIVITY_FARM, 20);
        priorities.put(BoardCase.ACTIVITY_FORAGE, 20);
        priorities.put(BoardCase.ACTIVITY_MINE, 15);
        priorities.put(BoardCase.ACTIVITY_CHOP, 15);
        priorities.put(BoardCase.ACTIVITY_REST, 5);
        priorities.put(BoardCase.REPAIR_BUILDING, 90);
        priorities.put(BoardCase.REPAIR_TOOL, 85);
        priorities.put(BoardCase.EAT_TO_FULL, 80);
        priorities.put(BoardCase.CAPTURE_ANIMAL, 75);
        priorities.put(BoardCase.RESTOCK_FOOD, 70);
        priorities.put(BoardCase.BUILD_PEN, 65);
        priorities.put(BoardCase.LURE_ANIMAL, 65);
        priorities.put(BoardCase.DISMANTLE_SHELTER, 65);
        priorities.put(BoardCase.RELOCATE_BUILDING, 60);
        priorities.put(BoardCase.BREED_ANIMALS, 55);
        priorities.put(BoardCase.SHEAR_SHEEP, 55);
        priorities.put(BoardCase.HARVEST_ORCHARD, 30);
        priorities.put(BoardCase.HARVEST_HIVE, 55);
        priorities.put(BoardCase.HERD_CULL, 50);
        priorities.put(BoardCase.COLLECT_DROPS, 55);
        priorities.put(BoardCase.HUNT_WILD_SHEEP, 50);
        for (BoardCase build : BUILDS) {
            priorities.put(build, 50);
        }
        priorities.put(BoardCase.GATHER_MATERIALS, 45);
        priorities.put(BoardCase.SUPPLY_SITE, 40);
        priorities.put(BoardCase.TRANSFER_ITEM, 45);
        priorities.put(BoardCase.MINE_LEFT_ORE, 40);
        priorities.put(BoardCase.CRAFT_SHEARS, 40);
        priorities.put(BoardCase.CRAFT_BED, 40);
        priorities.put(BoardCase.WEAVE_WOOL, 40);
        priorities.put(BoardCase.CRAFTING_STATION, 40);
        priorities.put(BoardCase.CRAFT_ANVIL, 50);
        priorities.put(BoardCase.CRAFT_SMITHING_TABLE, 50);
        priorities.put(BoardCase.CRAFT_SMOKER, 50);
        priorities.put(BoardCase.CRAFT_FURNACE, 40);
        priorities.put(BoardCase.CRAFT_TORCHES, 70);
        priorities.put(BoardCase.SMELT_CHARCOAL, 70);
        priorities.put(BoardCase.FISH, 50);
        priorities.put(BoardCase.STOCK_CHESTS, 40);
        priorities.put(BoardCase.CRAFT_WOODEN_PICKAXE, 65);
        priorities.put(BoardCase.CRAFT_WOODEN_AXE, 65);
        priorities.put(BoardCase.CRAFT_WOODEN_SHOVEL, 65);
        priorities.put(BoardCase.CRAFT_STONE_PICKAXE, 65);
        priorities.put(BoardCase.CRAFT_STONE_AXE, 65);
        priorities.put(BoardCase.CRAFT_STONE_SHOVEL, 65);
        priorities.put(BoardCase.CRAFT_STONE_SWORD, 40);
        priorities.put(BoardCase.CRAFT_IRON_PICKAXE, 40);
        priorities.put(BoardCase.CRAFT_IRON_AXE, 40);
        priorities.put(BoardCase.CRAFT_IRON_SHOVEL, 40);
        priorities.put(BoardCase.CRAFT_IRON_SWORD, 40);
        priorities.put(BoardCase.CRAFT_DIAMOND_PICKAXE, 40);
        priorities.put(BoardCase.CRAFT_DIAMOND_AXE, 40);
        priorities.put(BoardCase.CRAFT_DIAMOND_SHOVEL, 40);
        priorities.put(BoardCase.CRAFT_DIAMOND_SWORD, 40);
        priorities.put(BoardCase.FILL_CRATER, 35);
        priorities.put(BoardCase.ENCHANT_SHELVES, 30);
        return Map.copyOf(priorities);
    }
}
