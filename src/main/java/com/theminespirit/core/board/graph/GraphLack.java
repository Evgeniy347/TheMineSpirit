package com.theminespirit.core.board.graph;

import java.util.List;
import java.util.Map;

public final class GraphLack {
    private GraphLack() {}

    public static int threshold(SupplyGraph.Item item, GraphState state) {
        int stage = item.perSpirit() ? 1 : state.stage();
        return (int) Math.ceil(item.xmin() * state.spirits() * stage * item.k());
    }

    public static boolean lacking(SupplyGraph graph, GraphState state, String node) {
        if (graph.isGroup(node)) {
            List<String> members = graph.members(node);
            SupplyGraph.Item first = graph.items().get(members.get(0));
            int have = members.stream().mapToInt(state::count).sum();
            return first != null && have < threshold(first, state);
        }
        SupplyGraph.Item item = graph.items().get(node);
        if (item != null && item.stage() <= state.stage()) {
            return state.count(node) < threshold(item, state);
        }
        if (graph.isTool(node)) {
            int split = node.indexOf('_');
            return state.bestTier(node.substring(split + 1)) < graph.rank(node.substring(0, split));
        }
        return false;
    }

    static int count(SupplyGraph graph, GraphState state, String node) {
        if (graph.isGroup(node)) {
            return graph.members(node).stream().mapToInt(state::count).sum();
        }
        return state.count(node);
    }

    static boolean housed(GraphState state, SupplyGraph.Way way) {
        return way.building() == null || state.built(way.building()) == GraphState.Built.BUILT;
    }

    static boolean stocked(SupplyGraph graph, GraphState state, Map<String, Integer> needs) {
        return needs.entrySet().stream().allMatch(entry -> count(graph, state, entry.getKey()) >= entry.getValue());
    }

    static double share(SupplyGraph graph, GraphState state, Map<String, Integer> needs) {
        int total = needs.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            return 1.0;
        }
        int have = needs.entrySet().stream()
                .mapToInt(entry -> Math.min(entry.getValue(), Math.max(0, count(graph, state, entry.getKey()))))
                .sum();
        return (double) have / total;
    }

    static boolean ready(SupplyGraph graph, GraphState state, String building) {
        SupplyGraph.Building known = graph.buildings().get(building);
        return known != null && share(graph, state, known.materials()) >= graph.buildStartShare();
    }
}
