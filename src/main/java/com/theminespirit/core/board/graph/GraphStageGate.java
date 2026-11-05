package com.theminespirit.core.board.graph;

import java.util.Objects;

public final class GraphStageGate {
    private GraphStageGate() {}

    public static boolean buildingReady(GraphFacts facts, String id) {
        Objects.requireNonNull(facts, "Факты графа не могут быть null: по ним решается готовность этапа");
        GraphFacts.Building building = facts.buildings().get(id);
        return building != null && !building.unknown() && building.missing().isEmpty() && building.lotReady();
    }

    public static boolean lotsReady(GraphFacts facts) {
        GraphSiteFacts.Terrain terrain = facts.terrain();
        return !terrain.unknown() && terrain.cut().isEmpty() && terrain.fill().isEmpty();
    }

    public static boolean straysReady(GraphFacts facts) {
        return facts.strays().isEmpty();
    }
}
