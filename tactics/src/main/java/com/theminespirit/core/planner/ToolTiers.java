package com.theminespirit.core.planner;

import java.util.List;

final class ToolTiers {
    private static final List<String> PICKAXES =
            List.of("wooden_pickaxe", "stone_pickaxe", "iron_pickaxe", "diamond_pickaxe", "netherite_pickaxe");

    private ToolTiers() {}

    static List<String> acceptable(String item) {
        String wanted = normalize(item);
        int tier = PICKAXES.indexOf(wanted);
        return tier < 0 ? List.of(wanted) : PICKAXES.subList(tier, PICKAXES.size());
    }

    static String normalize(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }
}
