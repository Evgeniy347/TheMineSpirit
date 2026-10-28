package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphWorldTagTests {
    @Test
    void world_BlocksAndCreatures_TagsOfGraphWays() {
        Set<String> world = GraphNames.world(
                Set.of(
                        "jungle_log",
                        "minecraft:birch_log",
                        "stripped_oak_log",
                        "red_sand",
                        "clay",
                        "poppy",
                        "blue_orchid",
                        "lilac",
                        "peony",
                        "sunflower",
                        "lily_of_the_valley",
                        "orange_tulip",
                        "pink_petals",
                        "brown_mushroom",
                        "red_mushroom",
                        "bamboo",
                        "kelp",
                        "vine",
                        "mud",
                        "mangrove_roots",
                        "deepslate_diamond_ore",
                        "stone"),
                Set.of("rabbits", "glow_squid", "armadillo", "sheep:black"));

        assertEquals(
                Set.of(
                        "tree:jungle",
                        "tree:birch",
                        "red_sand",
                        "clay",
                        "flower:poppy",
                        "flower:blue_orchid",
                        "flower:lilac",
                        "flower:peony",
                        "flower:sunflower",
                        "flower:lily_of_the_valley",
                        "flower:orange_tulip",
                        "flower:pink_petals",
                        "mushrooms",
                        "bamboo_jungle",
                        "ocean",
                        "biome:jungle",
                        "mangrove_swamp",
                        "ore:diamond_found",
                        "rabbits",
                        "glow_squid",
                        "armadillo",
                        "sheep:black"),
                world,
                "Округа для путей графа: породы по брёвнам (ободранные — не дерево), красный песок отдельно, глина, "
                        + "цветы, грибы, бамбук, ламинария, лианы, грязь и корни мангров, найденная алмазная руда, "
                        + "живность; иначе путь «world» не активируется: " + world);
    }

    @Test
    void blockTag_FlowerMushroomAndBiomeBearing_ReturnsWayTag() {
        Map<String, String> tags = Map.of(
                "minecraft:blue_orchid", "flower:blue_orchid",
                "lilac", "flower:lilac",
                "brown_mushroom", "mushrooms",
                "bamboo", "bamboo_jungle",
                "kelp_plant", "ocean",
                "vine", "biome:jungle",
                "mud", "mangrove_swamp",
                "mangrove_roots", "mangrove_swamp");

        tags.forEach((block, tag) -> assertEquals(
                Optional.of(tag),
                GraphNames.blockTag(block),
                "Блок " + block + " — метка мира " + tag + " для пути графа: иначе путь не активируется"));
        assertEquals(
                Optional.empty(),
                GraphNames.blockTag("stone"),
                "Обычный камень не метка мира: лишние теги активировали бы пути там, где ресурса нет");
    }
}
