package com.theminespirit.core.board.graph;

import java.util.List;

final class ToolTiers {
    private ToolTiers() {}

    static List<String> wanted(SupplyGraph graph, GraphState state, GraphReach reach, String kind, int current) {
        List<String> above = graph.tiers().stream()
                .filter(tier -> graph.rank(tier) > current)
                .toList();
        int ready = above.stream()
                .filter(tier -> reach.producers(tier + "_" + kind).stream()
                        .anyMatch(way -> !way.consumes().isEmpty()
                                && (way.building() == null || state.built(way.building()) == GraphState.Built.BUILT)
                                && GraphLack.stocked(graph, state, way.consumes())))
                .mapToInt(graph::rank)
                .max()
                .orElse(current);
        return above.stream().filter(tier -> graph.rank(tier) >= ready).toList();
    }
}
