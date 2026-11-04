package com.theminespirit.core.board.graph;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class StoreInputs {
    public record FurnaceSlots(
            @Nullable String input,
            @Nullable String fuel,
            @Nullable String output) {}

    private StoreInputs() {}

    public static GraphSiteFacts.StageReqs stageReqs(
            GraphSiteFacts.StageReqs base, StoreMinimums settings, SupplyGraph graph, List<GraphFacts.Chest> chests) {
        Map<String, Integer> minimum = new LinkedHashMap<>(base.minimum());
        Map<String, Integer> actual = new LinkedHashMap<>(base.actual());
        Map<String, Integer> stored = counts(chests);
        settings.items().forEach((item, entry) -> {
            minimum.put(item, entry.target());
            actual.put(item, count(graph, stored, item));
        });
        return new GraphSiteFacts.StageReqs(minimum, actual);
    }

    public static GraphFacts.Station furnaceStation(GraphFacts.Station station, FurnaceSlots slots, boolean lit) {
        return new GraphFacts.Station(
                station.id(),
                station.type(),
                station.position(),
                lit,
                name(slots.fuel()),
                name(slots.input()),
                name(slots.output()));
    }

    private static Map<String, Integer> counts(List<GraphFacts.Chest> chests) {
        Map<String, Integer> counts = new HashMap<>();
        for (GraphFacts.Chest chest : chests) {
            chest.contents().forEach((item, count) -> counts.merge(GraphNames.graphItem(item), count, Integer::sum));
        }
        return counts;
    }

    private static int count(SupplyGraph graph, Map<String, Integer> stored, String item) {
        if (graph.isGroup(item)) {
            return graph.members(item).stream()
                    .mapToInt(member -> stored.getOrDefault(member, 0))
                    .sum();
        }
        return stored.getOrDefault(item, 0);
    }

    private static @Nullable String name(@Nullable String item) {
        return item == null ? null : GraphNames.graphItem(item);
    }
}
