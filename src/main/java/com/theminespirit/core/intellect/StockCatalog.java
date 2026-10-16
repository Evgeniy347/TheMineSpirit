package com.theminespirit.core.intellect;

import java.util.Map;
import java.util.Set;

public final class StockCatalog {
    public static final Set<String> ORE_IDS = Set.of("minecraft:raw_iron", "minecraft:raw_gold",
            "minecraft:raw_copper", "minecraft:coal", "minecraft:diamond", "minecraft:emerald",
            "minecraft:lapis_lazuli", "minecraft:redstone", "minecraft:quartz", "minecraft:amethyst_shard",
            "minecraft:copper_ingot", "minecraft:gold_ingot", "minecraft:gold_nugget");
    public static final Set<String> IRON_IDS = Set.of("minecraft:iron_ingot", "minecraft:raw_iron",
            "minecraft:iron_block");
    public static final Set<String> WEAPON_IDS = Set.of("minecraft:wooden_sword", "minecraft:stone_sword",
            "minecraft:iron_sword", "minecraft:diamond_sword", "minecraft:netherite_sword", "minecraft:bow",
            "minecraft:crossbow", "minecraft:trident", "minecraft:shield", "minecraft:iron_axe");
    public static final Set<String> SEED_IDS = Set.of("minecraft:wheat_seeds", "minecraft:carrot",
            "minecraft:potato", "minecraft:beetroot_seeds");

    private final NutritionTable table;

    public StockCatalog(NutritionTable table) {
        if (table == null) {
            throw new IllegalArgumentException(
                    "Подсчёт запасов без таблицы сытности не идёт: еда ранжируется по данным, а не по коду");
        }
        this.table = table;
    }

    public static NutritionTable defaultTable() {
        return new NutritionTable(Map.ofEntries(Map.entry("minecraft:bread", 5.0),
                Map.entry("minecraft:cooked_beef", 8.0), Map.entry("minecraft:cooked_porkchop", 8.0),
                Map.entry("minecraft:cooked_chicken", 6.0), Map.entry("minecraft:baked_potato", 5.0),
                Map.entry("minecraft:carrot", 3.0), Map.entry("minecraft:apple", 4.0),
                Map.entry("minecraft:golden_carrot", 6.0), Map.entry("minecraft:cooked_mutton", 6.0),
                Map.entry("minecraft:cooked_rabbit", 5.0), Map.entry("minecraft:pumpkin_pie", 8.0),
                Map.entry("minecraft:wheat", 1.0), Map.entry("minecraft:potato", 1.0),
                Map.entry("minecraft:beetroot", 1.0)));
    }

    public StockLots tally(Map<String, Integer> items) {
        if (items == null) {
            throw new IllegalArgumentException(
                    "Подсчёт запасов без описи не идёт: пустой набор задают пустой картой");
        }
        int foodItems = 0;
        double foodNutrition = 0;
        int ore = 0;
        int iron = 0;
        int weapons = 0;
        int seeds = 0;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            String id = entry.getKey();
            Integer count = entry.getValue();
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Предмет без имени в описи: запасы считаются по id предметов");
            }
            if (count == null || count < 0) {
                throw new IllegalArgumentException(
                        "Предметов " + id + " = " + count + ": счёт обязан быть >= 0");
            }
            Double satiety = table.nutrition().get(id);
            if (satiety != null) {
                foodItems += count;
                foodNutrition += satiety * count;
            }
            if (ORE_IDS.contains(id)) {
                ore += count;
            }
            if (IRON_IDS.contains(id)) {
                iron += count;
            }
            if (WEAPON_IDS.contains(id)) {
                weapons += count;
            }
            if (SEED_IDS.contains(id)) {
                seeds += count;
            }
        }
        return new StockLots(foodItems, foodNutrition, ore, iron, weapons, seeds);
    }
}
