package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StoreRules {
    public static final String RESOURCE = "data/theminespirit/board/graph.d/farm.json";

    public record Config(
            int reservePercent,
            Map<String, Integer> minimums,
            Map<String, String> toolMinimums,
            int maxItemTypesPerChest) {}

    private StoreRules() {}

    public static Config config() {
        JsonNode root = JsonResource.parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
        JsonNode store = root.path("farmStore");
        Map<String, Integer> minimums = new LinkedHashMap<>();
        store.path("minimums")
                .properties()
                .forEach(entry -> minimums.put(entry.getKey(), entry.getValue().asInt()));
        Map<String, String> tools = new LinkedHashMap<>();
        store.path("toolMinimums")
                .properties()
                .forEach(entry -> tools.put(entry.getKey(), entry.getValue().asText()));
        return new Config(
                store.path("reservePercent").asInt(),
                Map.copyOf(minimums),
                Map.copyOf(tools),
                store.path("maxItemTypesPerChest").asInt(6));
    }

    public static Map<String, Integer> shortfalls(
            Map<String, Integer> minimums, Map<String, Integer> actual, int reservePercent) {
        Map<String, Integer> shortfall = new LinkedHashMap<>();
        minimums.forEach((node, amount) -> {
            int lack = withReserve(amount, reservePercent) - actual.getOrDefault(node, 0);
            if (lack > 0) {
                shortfall.put(node, lack);
            }
        });
        return shortfall;
    }

    public static Map<String, Integer> groupCounts(
            SupplyGraph graph, Map<String, Integer> actual, Collection<String> nodes) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String node : nodes) {
            int count = graph.isGroup(node)
                    ? graph.members(node).stream()
                            .mapToInt(member -> actual.getOrDefault(member, 0))
                            .sum()
                    : actual.getOrDefault(node, 0);
            counts.put(node, count);
        }
        return counts;
    }

    public static Map<String, Integer> actual(List<GraphFacts.Chest> chests, Map<String, GraphFacts.Spirit> spirits) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (GraphFacts.Chest chest : chests) {
            chest.contents().forEach((item, count) -> counts.merge(GraphNames.graphItem(item), count, Integer::sum));
        }
        for (GraphFacts.Spirit spirit : spirits.values()) {
            spirit.inventory().forEach((item, count) -> counts.merge(GraphNames.graphItem(item), count, Integer::sum));
        }
        return counts;
    }

    public static int mixedChests(List<GraphFacts.Chest> chests, int limit) {
        if (limit <= 0) {
            return 0;
        }
        return (int) chests.stream()
                .filter(chest -> chest.contents().size() >= limit)
                .count();
    }

    public record Signals(
            boolean shortfall,
            boolean storageFull,
            boolean mixed,
            boolean carried,
            boolean furnaceReady,
            boolean furnaceNeedsFuel) {}

    public static Set<String> events(Set<String> base, Signals signals) {
        Set<String> events = new LinkedHashSet<>(base);
        if (signals.shortfall()) {
            events.add("store_minimum");
        }
        if (signals.storageFull()) {
            events.add("storage_full");
        }
        if (signals.mixed()) {
            events.add("storage_mixed");
        }
        if (signals.carried()) {
            events.add("transfer_pending");
        }
        if (signals.furnaceReady()) {
            events.add("furnace_ready");
        }
        if (signals.furnaceNeedsFuel()) {
            events.add("furnace_needs_fuel");
        }
        return Set.copyOf(events);
    }

    public static int withReserve(int amount, int reservePercent) {
        return amount + (int) Math.ceil(amount * Math.max(0, reservePercent) / 100.0);
    }
}
