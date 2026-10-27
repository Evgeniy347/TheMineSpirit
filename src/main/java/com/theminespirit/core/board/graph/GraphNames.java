package com.theminespirit.core.board.graph;

import com.theminespirit.core.building.WoodSpecies;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class GraphNames {
    public static final String FOOD = "food";
    private static final String NAMESPACE = "minecraft:";
    private static final String OAK_PREFIX = "oak_";
    private static final String FOREIGN = "other:";
    private static final Map<String, String> SUFFIXES = Map.of("_bed", "bed", "_wool", "wool");
    private static final Map<String, String> ALIASES = Map.of("charcoal", "coal");

    private GraphNames() {}

    public static String item(String graphItem, String wood) {
        Objects.requireNonNull(graphItem, "Предмет графа не может быть null: его переводят в предмет мира");
        return WoodSpecies.swap(graphItem, wood);
    }

    public static String graphItem(String item, String wood) {
        Objects.requireNonNull(item, "Предмет мира не может быть null: его переводят в узел графа");
        WoodSpecies.check(wood);
        String name = item.startsWith(NAMESPACE) ? item.substring(NAMESPACE.length()) : item;
        Optional<String> species = WoodSpecies.speciesOf(name);
        if (species.isPresent() && species.orElseThrow().equals(wood)) {
            return OAK_PREFIX + name.substring(wood.length() + 1);
        }
        if (species.isPresent()) {
            return FOREIGN + name;
        }
        for (Map.Entry<String, String> suffix : SUFFIXES.entrySet()) {
            if (name.endsWith(suffix.getKey())) {
                return suffix.getValue();
            }
        }
        return ALIASES.getOrDefault(name, name);
    }

    public static Map<String, Integer> stock(Map<String, Integer> available, String wood, Set<String> foods) {
        Objects.requireNonNull(available, "Запас не может быть null: пустой задают пустой картой");
        Objects.requireNonNull(foods, "Список еды не может быть null: без еды — пустой набор");
        Map<String, Integer> counts = new HashMap<>();
        available.forEach((item, count) -> {
            counts.merge(graphItem(item, wood), count, Integer::sum);
            String name = item.startsWith(NAMESPACE) ? item.substring(NAMESPACE.length()) : item;
            if (foods.contains(name) || foods.contains(NAMESPACE + name)) {
                counts.merge(FOOD, count, Integer::sum);
            }
        });
        return Map.copyOf(counts);
    }

    public static Map<String, Integer> need(Map<String, Integer> need, String wood) {
        Objects.requireNonNull(need, "Нужда не может быть null: пустую задают пустой картой");
        Map<String, Integer> counts = new HashMap<>();
        need.forEach((item, count) -> counts.merge(graphItem(item, wood), count, Integer::sum));
        return Map.copyOf(counts);
    }
}
