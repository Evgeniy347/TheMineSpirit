package com.theminespirit.core.board.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GraphWhy {
    private GraphWhy() {}

    public static String building(SupplyGraph graph, GraphState state, String id) {
        Objects.requireNonNull(graph, "Граф не может быть null: постройка берётся из него");
        Objects.requireNonNull(state, "Состояние не может быть null: причина считается по нему");
        SupplyGraph.Building building = graph.buildings().get(id);
        if (building == null) {
            return "постройки " + id + " нет в графе";
        }
        List<String> reasons = new ArrayList<>();
        if (building.tool() != null && state.bestTier(building.tool()) < 0) {
            reasons.add("нет инструмента " + building.tool());
        }
        for (String required : building.requires()) {
            if (state.built(required) != GraphState.Built.BUILT) {
                reasons.add("не готова " + required);
            }
        }
        Map<String, Integer> materials = state.materials().getOrDefault(id, building.materials());
        double share = GraphLack.share(graph, state, materials);
        if (share < graph.buildStartShare()) {
            reasons.add(
                    "материалов " + Math.round(share * 100) + "% < " + Math.round(graph.buildStartShare() * 100) + "%");
        }
        return reasons.isEmpty() ? "граф не назвал причину" : String.join(", ", reasons);
    }
}
