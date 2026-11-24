package com.theminespirit.core.board.graph;

import java.util.Map;

public final class GraphFactsUpdates {
    private GraphFactsUpdates() {}

    public static GraphFacts withStations(GraphFacts base, Map<String, GraphFacts.Station> stations) {
        return new GraphFacts(
                base.spirits(),
                base.threats(),
                base.chests(),
                base.resources(),
                stations,
                base.buildings(),
                base.strays(),
                base.terrain(),
                base.stageReqs(),
                base.feedback(),
                base.players(),
                base.planBreaches());
    }

    public static GraphFacts withStageReqs(GraphFacts base, GraphSiteFacts.StageReqs reqs) {
        return new GraphFacts(
                base.spirits(),
                base.threats(),
                base.chests(),
                base.resources(),
                base.stations(),
                base.buildings(),
                base.strays(),
                base.terrain(),
                reqs,
                base.feedback(),
                base.players(),
                base.planBreaches());
    }
}
