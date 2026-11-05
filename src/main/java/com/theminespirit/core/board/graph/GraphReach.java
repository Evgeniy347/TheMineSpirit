package com.theminespirit.core.board.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class GraphReach {
    private final SupplyGraph graph;
    private final GraphState state;
    private final Map<String, List<SupplyGraph.Way>> producers = new HashMap<>();
    private final Map<String, SupplyGraph.Way> ways = new HashMap<>();
    private final Map<String, Boolean> sourced = new HashMap<>();

    GraphReach(SupplyGraph graph, GraphState state) {
        this.graph = graph;
        this.state = state;
        for (SupplyGraph.Way way : graph.ways()) {
            producers.computeIfAbsent(way.makes(), key -> new ArrayList<>()).add(way);
            ways.putIfAbsent(way.id(), way);
        }
    }

    List<SupplyGraph.Way> producers(String node) {
        return producers.getOrDefault(node, List.of());
    }

    Optional<SupplyGraph.Way> way(String id) {
        return Optional.ofNullable(ways.get(id));
    }

    SupplyGraph.@Nullable Way find(String id) {
        return ways.get(id);
    }

    boolean available(SupplyGraph.Way way) {
        if (way.world() != null && !state.world().contains(way.world())) {
            return false;
        }
        for (String input : way.consumes().keySet()) {
            SupplyGraph.Item item = graph.items().get(input);
            if (item != null && item.stage() > state.stage()) {
                return false;
            }
        }
        SupplyGraph.Building building =
                way.building() == null ? null : graph.buildings().get(way.building());
        if (building == null) return true;
        boolean planned = !building.optional() || state.built(building.id()) != GraphState.Built.ABSENT;
        return building.stage() <= state.stage() && planned;
    }

    boolean sourced(String node) {
        Boolean known = sourced.get(node);
        if (known != null) {
            return known;
        }
        boolean value = computeSourced(node);
        sourced.put(node, value);
        return value;
    }

    private boolean computeSourced(String node) {
        if (graph.isGroup(node)) {
            return graph.members(node).stream().anyMatch(this::sourced);
        }
        List<SupplyGraph.Way> candidates = producers(node);
        return candidates.isEmpty() || candidates.stream().anyMatch(this::reachable);
    }

    private boolean reachable(SupplyGraph.Way way) {
        return available(way)
                && way.consumes().keySet().stream()
                        .allMatch(input -> GraphLack.count(graph, state, input) > 0 || sourcedShallow(input));
    }

    private boolean sourcedShallow(String node) {
        if (graph.isGroup(node)) {
            return graph.members(node).stream().anyMatch(this::sourcedShallow);
        }
        return producers(node).stream().anyMatch(this::available);
    }
}
