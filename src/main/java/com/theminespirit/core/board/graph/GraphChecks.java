package com.theminespirit.core.board.graph;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class GraphChecks {
    private GraphChecks() {}

    static void requireUniqueWays(List<SupplyGraph.Way> ways, String origin) {
        Set<String> ids = new HashSet<>();
        for (SupplyGraph.Way way : ways) {
            if (!ids.add(way.id())) {
                throw new IllegalStateException("Файл '" + origin + "': id пути '" + way.id()
                        + "' повторяется — разные пути с одним id путают причины заданий и дедупликацию доски");
            }
        }
    }

    static void requireKnownEdges(SupplyGraph graph, String origin) {
        for (SupplyGraph.Edge edge : graph.edges()) {
            Objects.requireNonNull(
                    graph.way(edge.way()).orElse(null),
                    "Файл '" + origin + "': ребро от '" + edge.lack() + "' ведёт к неизвестному пути " + edge.way());
        }
    }
}
