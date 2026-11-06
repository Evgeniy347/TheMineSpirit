package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class GraphSourceClosureTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void ways_EveryConsumedItem_HasSource() {
        Map<String, String> holes = new TreeMap<>();
        for (SupplyGraph.Way way : GRAPH.ways()) {
            for (Map<String, Integer> inputs : List.of(way.consumes(), way.needs())) {
                for (String input : inputs.keySet()) {
                    if (GRAPH.items().containsKey(input)
                            && GRAPH.producers(input).isEmpty()) {
                        holes.putIfAbsent(input, way.id());
                    }
                }
            }
        }

        assertEquals(
                Map.of(),
                holes,
                "Каждый предмет, который путь тратит или требует, обязан иметь путь добычи: иначе планировщик"
                        + " просит то, чего взять неоткуда. Дыры (предмет = путь, которому нужен) — добавь путь"
                        + " добычи в board/graph.json (неготовое — implemented=false)");
    }

    @Test
    void ways_LateVanillaSources_InDevelopment() {
        for (String way : List.of(
                "defeat_wither",
                "defeat_ender_dragon",
                "loot_elytra",
                "open_ominous_vault",
                "loot_prismarine_shard",
                "brush_sniffer_egg",
                "build_iron_golem")) {
            assertTrue(
                    GRAPH.way(way).isPresent() && !GRAPH.implemented(way),
                    "Поздняя ваниль (боссы, Энд, океан, испытательные залы, археология, големы) внесена"
                            + " как «в разработке»: " + way);
        }
    }
}
