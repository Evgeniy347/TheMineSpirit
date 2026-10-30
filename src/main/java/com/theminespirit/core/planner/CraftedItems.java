package com.theminespirit.core.planner;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CraftedItems {
    private static final List<String> FAMILIES = List.of(
            "_planks",
            "_sapling",
            "_propagule",
            "_fence",
            "_fence_gate",
            "_door",
            "_trapdoor",
            "_stairs",
            "_slab",
            "_button",
            "_pressure_plate",
            "_sign",
            "_banner",
            "_bed",
            "_carpet",
            "_pane",
            "_wall");

    private static final Set<String> EXACT = Set.of(
            "torch",
            "soul_torch",
            "redstone_torch",
            "crafting_table",
            "chest",
            "trapped_chest",
            "barrel",
            "furnace",
            "blast_furnace",
            "smoker",
            "smithing_table",
            "anvil",
            "chipped_anvil",
            "damaged_anvil",
            "bookshelf",
            "chiseled_bookshelf",
            "glass",
            "ladder",
            "scaffolding",
            "campfire",
            "soul_campfire",
            "cartography_table",
            "fletching_table",
            "brewing_stand",
            "cauldron",
            "composter",
            "lectern",
            "loom",
            "stonecutter",
            "grindstone",
            "bell",
            "beacon",
            "jukebox",
            "note_block",
            "target",
            "lever",
            "tripwire_hook",
            "lightning_rod",
            "chain",
            "iron_bars",
            "flower_pot");

    private CraftedItems() {}

    public static boolean isCrafted(String item) {
        Objects.requireNonNull(item, "Предмет не может быть null: по имени решают, крафт это или добыча");
        String name = item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
        if (EXACT.contains(name)) {
            return true;
        }
        for (String family : FAMILIES) {
            if (name.endsWith(family)) {
                return true;
            }
        }
        return false;
    }

    public static UnplannableGoalException error(String item) {
        return new UnplannableGoalException(
                item,
                "Предмет '" + item + "' появляется крафтом, а не добычей из мира: рецепт сейчас недоступен, "
                        + "поэтому размещённые копии в округе не ищутся (37 досок на постройках — не запас духа). "
                        + "Как надо: вернуть рецепт в знания крафта или добыть сырьё (брёвна) и скрафтить");
    }
}
