package com.theminespirit.core.board.graph;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class GraphKits {
    private static final String NAMESPACE = "minecraft:";
    private static final Map<String, String> TIER_ALIASES = Map.of("golden", "wooden", "netherite", "diamond");

    private GraphKits() {}

    public static Map<String, Integer> of(Collection<String> items, SupplyGraph graph) {
        Objects.requireNonNull(items, "Вещи духа не могут быть null: пустые руки — пустой список");
        Objects.requireNonNull(graph, "Граф не может быть null: по нему известны виды и уровни инструментов");
        Map<String, Integer> kit = new HashMap<>();
        for (String item : items) {
            String name = item.startsWith(NAMESPACE) ? item.substring(NAMESPACE.length()) : item;
            int split = name.lastIndexOf('_');
            if (split <= 0) {
                continue;
            }
            String kind = name.substring(split + 1);
            String tier = name.substring(0, split);
            int rank = graph.rank(TIER_ALIASES.getOrDefault(tier, tier));
            if (rank >= 0 && graph.kit().kinds().contains(kind)) {
                kit.merge(kind, rank, Math::max);
            }
        }
        return Map.copyOf(kit);
    }
}
