package com.theminespirit.core.board.graph;

import com.theminespirit.core.building.SchematicBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class GraphBuildingMaterials {
    private GraphBuildingMaterials() {}

    public static Map<String, Map<String, Integer>> of(GraphFacts facts, SupplyGraph graph) {
        Objects.requireNonNull(facts, "Факты графа не могут быть null: по ним считается недостача");
        Objects.requireNonNull(graph, "Граф не может быть null: по нему проверяются предметы");
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        facts.buildings().forEach((id, building) -> {
            if (building.unknown() || building.missing().isEmpty()) {
                return;
            }
            Map<String, Integer> items = new LinkedHashMap<>();
            building.missing().forEach((block, count) -> {
                String item = GraphNames.graphItem(SchematicBlocks.itemOf(block));
                if (graph.items().containsKey(item)) {
                    items.merge(item, count, Integer::sum);
                }
            });
            if (!items.isEmpty()) {
                out.put(id, Map.copyOf(items));
            }
        });
        return Map.copyOf(out);
    }

    public static Map<String, Map<String, Integer>> merged(
            Map<String, Map<String, Integer>> declared, GraphFacts facts, SupplyGraph graph) {
        Objects.requireNonNull(declared, "Объявленные материалы не могут быть null: без них — пустая карта");
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>(declared);
        of(facts, graph).forEach((id, missing) -> {
            GraphFacts.Building fact = facts.buildings().get(id);
            if (fact != null && !fact.unknown() && fact.auditPercent() > 0) {
                out.put(id, missing);
            } else {
                out.putIfAbsent(id, missing);
            }
        });
        return Map.copyOf(out);
    }
}
