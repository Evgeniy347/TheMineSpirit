package com.theminespirit.core.board;

import java.util.HashMap;
import java.util.Map;

final class BoardPrioritiesFixture {
    private BoardPrioritiesFixture() {}

    static Map<String, Integer> priorities() {
        Map<String, Integer> all = new HashMap<>(base());
        all.putAll(activities());
        return Map.copyOf(all);
    }

    private static Map<String, Integer> base() {
        return Map.ofEntries(
                Map.entry("REPAIR_FENCE", 100),
                Map.entry("REPAIR_BUILDING", 90),
                Map.entry("EAT_TO_FULL", 80),
                Map.entry("CAPTURE_ANIMAL", 75),
                Map.entry("RESTOCK_FOOD", 70),
                Map.entry("REPAIR_TOOL", 85),
                Map.entry("BUILD_PEN", 65),
                Map.entry("BUILD_WAREHOUSE", 50),
                Map.entry("LURE_ANIMAL", 65),
                Map.entry("DISMANTLE_SHELTER", 65),
                Map.entry("RELOCATE_BUILDING", 60),
                Map.entry("BREED_ANIMALS", 55),
                Map.entry("BREW_POTION", 45),
                Map.entry("COLLECT_DROPS", 55),
                Map.entry("SHEAR_SHEEP", 55),
                Map.entry("HARVEST_ORCHARD", 30),
                Map.entry("HARVEST_HIVE", 55),
                Map.entry("HERD_CULL", 50),
                Map.entry("HUNT_WILD_SHEEP", 50),
                Map.entry("CLEAR_GROUNDS", 50),
                Map.entry("BUILD_FENCE", 50),
                Map.entry("LIGHTING", 50),
                Map.entry("BUILD_HOUSE", 50),
                Map.entry("PLACE_BEEHIVE", 50),
                Map.entry("BUILD_ORCHARD", 50),
                Map.entry("BUILD_STREETS", 50),
                Map.entry("BUILD_MINE", 50),
                Map.entry("ENCHANT_ROOM", 50),
                Map.entry("CRAFT_ANVIL", 50),
                Map.entry("CRAFT_SMITHING_TABLE", 50),
                Map.entry("CRAFT_SMOKER", 50),
                Map.entry("CRAFT_FURNACE", 40),
                Map.entry("CRAFT_BREWING_STAND", 40),
                Map.entry("CRAFT_TORCHES", 70),
                Map.entry("SMELT_CHARCOAL", 70),
                Map.entry("FISH", 50),
                Map.entry("GRIND_BLAZE_POWDER", 40),
                Map.entry("HARVEST_NETHER_WART", 40),
                Map.entry("GATHER_MATERIALS", 45),
                Map.entry("SUPPLY_SITE", 40),
                Map.entry("TRANSFER_ITEM", 45),
                Map.entry("MINE_LEFT_ORE", 40),
                Map.entry("CRAFT_STONE_AXE", 65),
                Map.entry("CRAFT_STONE_PICKAXE", 65),
                Map.entry("CRAFT_STONE_SHOVEL", 65),
                Map.entry("CRAFT_STONE_SWORD", 40),
                Map.entry("CRAFT_IRON_PICKAXE", 40),
                Map.entry("CRAFT_IRON_AXE", 40),
                Map.entry("CRAFT_IRON_SHOVEL", 40),
                Map.entry("CRAFT_IRON_SWORD", 40),
                Map.entry("CRAFT_DIAMOND_PICKAXE", 40),
                Map.entry("CRAFT_DIAMOND_AXE", 40),
                Map.entry("CRAFT_DIAMOND_SHOVEL", 40),
                Map.entry("CRAFT_DIAMOND_SWORD", 40),
                Map.entry("CRAFT_WOODEN_AXE", 65),
                Map.entry("CRAFT_WOODEN_PICKAXE", 65),
                Map.entry("CRAFT_WOODEN_SHOVEL", 65),
                Map.entry("CRAFT_SHEARS", 40),
                Map.entry("CRAFT_BED", 40),
                Map.entry("WEAVE_WOOL", 40),
                Map.entry("CRAFTING_STATION", 40),
                Map.entry("STOCK_CHESTS", 40),
                Map.entry("FILL_CRATER", 35),
                Map.entry("ENCHANT_SHELVES", 30));
    }

    private static Map<String, Integer> activities() {
        return Map.ofEntries(
                Map.entry("ACTIVITY_COMBAT", 100),
                Map.entry("ACTIVITY_RETREAT", 100),
                Map.entry("ACTIVITY_RETURN", 30),
                Map.entry("ACTIVITY_GUARD", 30),
                Map.entry("ACTIVITY_ARM", 100),
                Map.entry("ACTIVITY_EXTERNAL", 95),
                Map.entry("ACTIVITY_EXPEDITION", 95),
                Map.entry("ACTIVITY_ESCAPE", 95),
                Map.entry("ACTIVITY_DEFEND", 29),
                Map.entry("ACTIVITY_UNLOAD", 25),
                Map.entry("ACTIVITY_CRAFT", 20),
                Map.entry("ACTIVITY_SMELT", 20),
                Map.entry("ACTIVITY_FARM", 20),
                Map.entry("ACTIVITY_FORAGE", 20),
                Map.entry("ACTIVITY_MINE", 15),
                Map.entry("ACTIVITY_CHOP", 15),
                Map.entry("ACTIVITY_REST", 5));
    }
}
