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
    private static final Map<String, String> SUFFIXES = Map.of("_bed", "bed");
    private static final String DIAMOND_FOUND = "ore:diamond_found";
    private static final Map<String, String> GROUND = Map.of(
            "sand", "sand",
            "red_sand", "red_sand",
            "clay", "clay",
            "cactus", "cactus",
            "poppy", "flower:poppy",
            "dandelion", "flower:dandelion",
            "cornflower", "flower:cornflower",
            "diamond_ore", DIAMOND_FOUND,
            "deepslate_diamond_ore", DIAMOND_FOUND);
    private static final String TREE = "tree:";
    private static final String LOG = "_log";
    private static final String MANGROVE_SAPLING = "mangrove_sapling";
    private static final String MANGROVE_PROPAGULE = "mangrove_propagule";

    private GraphNames() {}

    public static String item(SupplyGraph graph, String node, String wood) {
        Objects.requireNonNull(graph, "Граф не может быть null: по нему узнают группы пород");
        Objects.requireNonNull(node, "Узел графа не может быть null: его переводят в предмет мира");
        WoodSpecies.check(wood);
        if (!graph.isGroup(node)) {
            return worldItem(node);
        }
        List<String> members = graph.members(node);
        String member = members.stream()
                .filter(entry ->
                        WoodSpecies.speciesOf(entry).filter(wood::equals).isPresent())
                .findFirst()
                .orElse(members.get(0));
        return worldItem(member);
    }

    private static String worldItem(String node) {
        return MANGROVE_SAPLING.equals(node) ? MANGROVE_PROPAGULE : node;
    }

    public static String graphItem(String item) {
        Objects.requireNonNull(item, "Предмет мира не может быть null: его переводят в узел графа");
        String name = item.startsWith(NAMESPACE) ? item.substring(NAMESPACE.length()) : item;
        if (MANGROVE_PROPAGULE.equals(name)) {
            return MANGROVE_SAPLING;
        }
        if (WoodSpecies.speciesOf(name).isPresent()) {
            return name;
        }
        for (Map.Entry<String, String> suffix : SUFFIXES.entrySet()) {
            if (name.endsWith(suffix.getKey())) {
                return suffix.getValue();
            }
        }
        return name;
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

    public static Set<String> world(Set<String> blocks, Set<String> creatures) {
        Objects.requireNonNull(blocks, "Блоки округи не могут быть null: в округе ничего нет — пустой набор");
        Objects.requireNonNull(creatures, "Живность округи не может быть null: никого нет — пустой набор");
        Set<String> world = new HashSet<>(creatures);
        for (String block : blocks) {
            String name = block.startsWith(NAMESPACE) ? block.substring(NAMESPACE.length()) : block;
            if (name.endsWith(LOG)) {
                WoodSpecies.speciesOf(name).ifPresent(species -> world.add(TREE + species));
            }
            if (GROUND.containsKey(name)) {
                world.add(GROUND.get(name));
            }
        }
        return Set.copyOf(world);
    }
}
