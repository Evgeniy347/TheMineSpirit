package com.theminespirit.core.board.graph;

import com.theminespirit.core.building.WoodSpecies;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class GraphNames {
    public static final String FOOD = "food";
    private static final String NAMESPACE = "minecraft:";
    private static final Map<String, String> SUFFIXES = Map.of("_bed", "bed", "_wool", "wool");
    private static final Map<String, String> ALIASES = Map.of("charcoal", "coal");
    private static final Map<String, String> GROUND = Map.of("sand", "sand", "red_sand", "sand", "clay", "clay");
    private static final String TREE = "tree:";
    private static final String LOG = "_log";
    private static final String ANIMALS = "animals";

    private GraphNames() {}

    public static String item(SupplyGraph graph, String node, String wood) {
        Objects.requireNonNull(graph, "Граф не может быть null: по нему узнают группы пород");
        Objects.requireNonNull(node, "Узел графа не может быть null: его переводят в предмет мира");
        WoodSpecies.check(wood);
        if (!graph.isGroup(node)) {
            return node;
        }
        List<String> members = graph.members(node);
        return members.stream()
                .filter(member ->
                        WoodSpecies.speciesOf(member).filter(wood::equals).isPresent())
                .findFirst()
                .orElse(members.get(0));
    }

    public static String graphItem(String item) {
        Objects.requireNonNull(item, "Предмет мира не может быть null: его переводят в узел графа");
        String name = item.startsWith(NAMESPACE) ? item.substring(NAMESPACE.length()) : item;
        if (WoodSpecies.speciesOf(name).isPresent()) {
            return name;
        }
        for (Map.Entry<String, String> suffix : SUFFIXES.entrySet()) {
            if (name.endsWith(suffix.getKey())) {
                return suffix.getValue();
            }
        }
        return ALIASES.getOrDefault(name, name);
    }

    public static Map<String, Integer> stock(Map<String, Integer> available, Set<String> foods) {
        Objects.requireNonNull(available, "Запас не может быть null: пустой задают пустой картой");
        Objects.requireNonNull(foods, "Список еды не может быть null: без еды — пустой набор");
        Map<String, Integer> counts = new HashMap<>();
        available.forEach((item, count) -> {
            counts.merge(graphItem(item), count, Integer::sum);
            String name = item.startsWith(NAMESPACE) ? item.substring(NAMESPACE.length()) : item;
            if (foods.contains(name) || foods.contains(NAMESPACE + name)) {
                counts.merge(FOOD, count, Integer::sum);
            }
        });
        return Map.copyOf(counts);
    }

    public static Map<String, Integer> need(Map<String, Integer> need) {
        Objects.requireNonNull(need, "Нужда не может быть null: пустую задают пустой картой");
        Map<String, Integer> counts = new HashMap<>();
        need.forEach((item, count) -> counts.merge(graphItem(item), count, Integer::sum));
        return Map.copyOf(counts);
    }

    public static Set<String> world(Set<String> blocks, boolean animals) {
        Objects.requireNonNull(blocks, "Блоки округи не могут быть null: в округе ничего нет — пустой набор");
        Set<String> world = new HashSet<>();
        for (String block : blocks) {
            String name = block.startsWith(NAMESPACE) ? block.substring(NAMESPACE.length()) : block;
            if (name.endsWith(LOG)) {
                WoodSpecies.speciesOf(name).ifPresent(species -> world.add(TREE + species));
            }
            if (GROUND.containsKey(name)) {
                world.add(GROUND.get(name));
            }
        }
        if (animals) {
            world.add(ANIMALS);
        }
        return Set.copyOf(world);
    }
}
