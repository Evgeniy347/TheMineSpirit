package com.theminespirit.core.repair;

import static com.theminespirit.core.Lookup.present;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class RepairMaterials {
    private static final Map<String, String> BY_PREFIX = Map.ofEntries(
            Map.entry("wooden_", "minecraft:oak_planks"),
            Map.entry("stone_", "minecraft:cobblestone"),
            Map.entry("iron_", "minecraft:iron_ingot"),
            Map.entry("golden_", "minecraft:gold_ingot"),
            Map.entry("diamond_", "minecraft:diamond"),
            Map.entry("netherite_", "minecraft:netherite_ingot"),
            Map.entry("leather_", "minecraft:leather"),
            Map.entry("chainmail_", "minecraft:iron_ingot"));

    private static final List<String> PREFIXES_LONGEST_FIRST = BY_PREFIX.keySet().stream()
            .sorted((first, second) -> Integer.compare(second.length(), first.length()))
            .toList();

    private static final String MINECRAFT_PREFIX = "minecraft:";

    private RepairMaterials() {}

    public static Optional<String> materialFor(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        String path = itemId.startsWith(MINECRAFT_PREFIX) ? itemId.substring(MINECRAFT_PREFIX.length()) : itemId;
        for (String prefix : PREFIXES_LONGEST_FIRST) {
            if (path.startsWith(prefix)) {
                return Optional.of(present(BY_PREFIX, prefix, "Материалы ремонта"));
            }
        }
        return Optional.empty();
    }
}
