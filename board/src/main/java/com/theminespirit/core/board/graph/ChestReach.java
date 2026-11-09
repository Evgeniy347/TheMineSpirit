package com.theminespirit.core.board.graph;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ChestReach {
    private ChestReach() {}

    public static Map<String, Integer> reachableStock(Map<String, Integer> available, List<GraphFacts.Chest> chests) {
        Objects.requireNonNull(available, "Запас не может быть null: пустой задают пустой картой");
        Objects.requireNonNull(chests, "Сундуки не могут быть null: без них — пустой список");
        Map<String, Integer> unreachable = new HashMap<>();
        for (GraphFacts.Chest chest : chests) {
            if (chest.reach() != GraphFacts.Reach.NO_PATH) {
                continue;
            }
            chest.contents()
                    .forEach((item, count) -> unreachable.merge(GraphNames.graphItem(item), count, Integer::sum));
        }
        if (unreachable.isEmpty()) {
            return available;
        }
        Map<String, Integer> reachable = new LinkedHashMap<>();
        available.forEach((item, count) -> reachable.merge(GraphNames.graphItem(item), count, Integer::sum));
        unreachable.forEach((item, count) ->
                reachable.computeIfPresent(item, (known, total) -> total > count ? total - count : null));
        return Map.copyOf(reachable);
    }

    public static GraphFacts.Reach verdict(boolean loaded, boolean covered) {
        if (!loaded) {
            return GraphFacts.Reach.UNKNOWN;
        }
        return covered ? GraphFacts.Reach.PATH : GraphFacts.Reach.NO_PATH;
    }
}
