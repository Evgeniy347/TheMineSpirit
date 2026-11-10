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
    public static final String SOIL = "soil";
    private static final String NAMESPACE = "minecraft:";
    private static final Map<String, String> SUFFIXES = Map.of("_bed", "bed");
    private static final String DIAMOND_FOUND = "ore:diamond_found";
    private static final String GEODE = "geode";
    private static final String DRIPSTONE_CAVES = "dripstone_caves";
    private static final String LUSH_CAVES = "lush_caves";
    private static final Map<String, Set<String>> GROUND = worldTags();
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

    public static Set<String> blockWorld(String block) {
        Objects.requireNonNull(block, "Блок округи не может быть null: по нему ищется метка мира");
        String name = block.startsWith(NAMESPACE) ? block.substring(NAMESPACE.length()) : block;
        return GROUND.getOrDefault(name, Set.of());
    }

    public static Set<String> world(Set<String> blocks, Set<String> creatures) {
        Objects.requireNonNull(blocks, "Блоки округи не могут быть null: в округе ничего нет — пустой набор");
        Objects.requireNonNull(creatures, "Живность округи не может быть null: никого нет — пустой набор");
        Set<String> world = new HashSet<>(creatures);
        blocks.forEach(block -> world.addAll(blockWorld(block)));
        return Set.copyOf(world);
    }

    private static Map<String, Set<String>> worldTags() {
        Map<String, Set<String>> tags = new HashMap<>();
        tag(tags, "sand", "sand");
        tag(tags, "red_sand", "red_sand");
        tag(tags, "water", "water");
        tag(tags, "clay", "clay");
        tag(tags, "lava", "lava");
        tag(tags, "cactus", "cactus");
        tag(tags, "poppy", "flower:poppy");
        tag(tags, "dandelion", "flower:dandelion");
        tag(tags, "cornflower", "flower:cornflower");
        tag(tags, "blue_orchid", "flower:blue_orchid");
        tag(tags, "lilac", "flower:lilac");
        tag(tags, "peony", "flower:peony");
        tag(tags, "sunflower", "flower:sunflower");
        tag(tags, "lily_of_the_valley", "flower:lily_of_the_valley");
        tag(tags, "orange_tulip", "flower:orange_tulip");
        tag(tags, "pink_petals", "flower:pink_petals");
        tag(tags, "brown_mushroom", "mushrooms");
        tag(tags, "red_mushroom", "mushrooms");
        tag(tags, "bamboo", "bamboo_jungle");
        tag(tags, "kelp", "ocean");
        tag(tags, "kelp_plant", "ocean");
        tag(tags, "vine", "biome:jungle");
        tag(tags, "mud", "mangrove_swamp");
        tag(tags, "mangrove_roots", "mangrove_swamp");
        tag(tags, "diamond_ore", DIAMOND_FOUND);
        tag(tags, "deepslate_diamond_ore", DIAMOND_FOUND);
        tag(tags, "emerald_ore", "emerald_ore");
        tag(tags, "deepslate_emerald_ore", "emerald_ore");
        for (String block : List.of(
                "amethyst_block",
                "budding_amethyst",
                "amethyst_cluster",
                "large_amethyst_bud",
                "medium_amethyst_bud",
                "small_amethyst_bud")) {
            tag(tags, block, GEODE);
        }
        tag(tags, "pointed_dripstone", DRIPSTONE_CAVES);
        tag(tags, "dripstone_block", DRIPSTONE_CAVES);
        for (String block : List.of(
                "moss_block",
                "azalea_leaves",
                "flowering_azalea_leaves",
                "spore_blossom",
                "moss_carpet",
                "cave_vines",
                "cave_vines_plant",
                "glow_lichen")) {
            tag(tags, block, LUSH_CAVES);
        }
        tag(tags, "azalea", "azalea", LUSH_CAVES);
        tag(tags, "flowering_azalea", "azalea", LUSH_CAVES);
        return Map.copyOf(tags);
    }

    private static void tag(Map<String, Set<String>> tags, String block, String... world) {
        tags.put(block, Set.of(world));
    }
}
