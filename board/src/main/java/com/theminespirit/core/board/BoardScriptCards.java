package com.theminespirit.core.board;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class BoardScriptCards {
    private static final Map<String, BoardCase> CASES = cases();

    private BoardScriptCards() {}

    static Optional<BoardCase> of(String script) {
        return Optional.ofNullable(CASES.get(script));
    }

    private static Map<String, BoardCase> cases() {
        Map<String, BoardCase> cases = new LinkedHashMap<>();
        perceptiveScripts(cases);
        workScripts(cases);
        movementScripts(cases);
        exchangeScripts(cases);
        return Map.copyOf(cases);
    }

    private static void perceptiveScripts(Map<String, BoardCase> cases) {
        put(
                cases,
                BoardCase.ACTIVITY_SENSE,
                "scan",
                "scan_blocks",
                "scan_entities",
                "sense_hazard",
                "locate_block",
                "locate_tree",
                "locate_container",
                "look_at",
                "remember",
                "recall",
                "recall_all",
                "forget");
        put(cases, BoardCase.ACTIVITY_CHOP, "chop_tree", "chop_target", "strip_log");
        put(
                cases,
                BoardCase.ACTIVITY_MINE,
                "mine_vein",
                "dig_area",
                "dig_descent",
                "dig_descent_widen",
                "place_descent_stairs",
                "safe_mine");
        put(cases, BoardCase.ACTIVITY_GUARD, "patrol");
        put(cases, BoardCase.ACTIVITY_REST, "eat", "drink_potion", "sleep", "wait");
    }

    private static void workScripts(Map<String, BoardCase> cases) {
        put(
                cases,
                BoardCase.ACTIVITY_BUILD,
                "place_block",
                "pillar_up",
                "pillar_down",
                "build_schematic",
                "build_farm",
                "build_fence",
                "build_shelter",
                "cast_obsidian",
                "light_portal",
                "fill",
                "fill_crater",
                "clear_area",
                "light_area",
                "open",
                "use_block");
        put(
                cases,
                BoardCase.ACTIVITY_FARM,
                "till",
                "plant_crop",
                "harvest_crop",
                "bone_meal",
                "breed",
                "feed",
                "cull",
                "herd_check",
                "hunt",
                "capture",
                "fish",
                "bucket_fish",
                "fill_bucket",
                "pour_bucket",
                "shear",
                "milk",
                "collect_eggs",
                "brush",
                "harvest_hive",
                "farm_nether_wart");
        put(cases, BoardCase.ACTIVITY_CRAFT, "craft", "repair_tool");
        put(cases, BoardCase.ACTIVITY_ARM, "equip", "swap_tool");
        put(cases, BoardCase.ACTIVITY_FORAGE, "gather", "collect_drops", "acquire", "acquire_at");
        put(cases, BoardCase.ACTIVITY_ESCAPE, "dig_out");
        put(cases, BoardCase.ACTIVITY_UNDO, "undo_lease");
    }

    private static void movementScripts(Map<String, BoardCase> cases) {
        put(
                cases,
                BoardCase.ACTIVITY_MOVE,
                "goto",
                "goto_block",
                "walk_careful",
                "step_over",
                "climb",
                "approach",
                "swim",
                "cross_water",
                "bridge",
                "wander",
                "keep_distance");
        put(cases, BoardCase.ACTIVITY_EXPEDITION, "follow", "escort", "come_to_player", "explore");
        put(cases, BoardCase.ACTIVITY_RETURN, "return_home");
        put(cases, BoardCase.ACTIVITY_RETREAT, "retreat");
        put(cases, BoardCase.ACTIVITY_COMBAT, "attack", "flee", "disengage", "set_combat_mode");
        put(cases, BoardCase.ACTIVITY_DEFEND, "defend");
    }

    private static void exchangeScripts(Map<String, BoardCase> cases) {
        put(cases, BoardCase.ACTIVITY_SOCIAL, "say", "report", "meet", "give", "deliver", "drop_to_player");
        put(cases, BoardCase.ACTIVITY_UNLOAD, "take", "deposit", "withdraw", "unload", "sort_chest");
        put(cases, BoardCase.ACTIVITY_SMELT, "smelt", "brew");
        put(cases, BoardCase.ACTIVITY_EXTERNAL, "close_door", "set_camp", "clear_camp");
    }

    private static void put(Map<String, BoardCase> cases, BoardCase boardCase, String... scripts) {
        for (String script : scripts) {
            BoardCase previous = cases.put(script, boardCase);
            if (previous != null) {
                throw new IllegalArgumentException("Скрипт " + script + " уже показан кейсом " + previous
                        + ": карточка обязана называть одно действие одной строкой");
            }
        }
    }
}
