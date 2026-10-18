package com.theminespirit.core.mining;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PickaxeTiers {
    private static final Map<String, Integer> ORE = ore();
    private static final Map<String, Integer> BLOCK = block();

    private PickaxeTiers() {
    }

    public static Map<String, Integer> oreTiers() {
        return ORE;
    }

    public static Map<String, Integer> blockTiers() {
        return BLOCK;
    }

    private static Map<String, Integer> ore() {
        Map<String, Integer> tiers = new LinkedHashMap<>();
        tiers.put("any", 1);
        tiers.put("coal", 1);
        tiers.put("iron", 2);
        tiers.put("copper", 2);
        tiers.put("gold", 3);
        tiers.put("redstone", 3);
        tiers.put("lapis", 2);
        tiers.put("diamond", 3);
        tiers.put("emerald", 3);
        tiers.put("quartz", 1);
        tiers.put("netherite", 4);
        return Collections.unmodifiableMap(tiers);
    }

    private static Map<String, Integer> block() {
        Map<String, Integer> tiers = new LinkedHashMap<>();
        for (String name : new String[]{
                "stone", "deepslate", "granite", "diorite", "andesite", "tuff", "calcite", "dripstone_block",
                "basalt", "smooth_basalt", "blackstone", "netherrack", "end_stone", "sandstone", "red_sandstone",
                "crimson_nylium", "warped_nylium", "glowstone", "magma_block", "amethyst_block", "prismarine",
                "prismarine_bricks", "dark_prismarine", "sea_lantern", "coal_ore", "deepslate_coal_ore",
                "nether_quartz_ore"}) {
            tiers.put(name, 1);
        }
        for (String name : new String[]{
                "iron_ore", "deepslate_iron_ore", "copper_ore", "deepslate_copper_ore", "lapis_ore",
                "deepslate_lapis_ore", "raw_iron_block", "raw_copper_block"}) {
            tiers.put(name, 2);
        }
        for (String name : new String[]{
                "gold_ore", "deepslate_gold_ore", "nether_gold_ore", "redstone_ore", "deepslate_redstone_ore",
                "diamond_ore", "deepslate_diamond_ore", "emerald_ore", "deepslate_emerald_ore", "raw_gold_block"}) {
            tiers.put(name, 3);
        }
        for (String name : new String[]{"obsidian", "ancient_debris"}) {
            tiers.put(name, 4);
        }
        return Collections.unmodifiableMap(tiers);
    }
}
