package com.theminespirit.core.board.graph;

import java.util.List;
import java.util.Map;

final class GraphFactsCopies {
    private GraphFactsCopies() {}

    static GraphFacts chests(GraphFacts base, List<GraphFacts.Chest> known) {
        return new GraphFacts(
                base.spirits(),
                base.threats(),
                known,
                base.resources(),
                base.stations(),
                base.buildings(),
                base.strays(),
                base.terrain(),
                base.stageReqs(),
                base.feedback(),
                base.players(),
                base.planBreaches());
    }

    static GraphFacts threats(GraphFacts base, List<GraphFacts.Threat> known) {
        return new GraphFacts(
                base.spirits(),
                known,
                base.chests(),
                base.resources(),
                base.stations(),
                base.buildings(),
                base.strays(),
                base.terrain(),
                base.stageReqs(),
                base.feedback(),
                base.players(),
                base.planBreaches());
    }

    static GraphFacts spirits(GraphFacts base, Map<String, GraphFacts.Spirit> known) {
        return new GraphFacts(
                known,
                base.threats(),
                base.chests(),
                base.resources(),
                base.stations(),
                base.buildings(),
                base.strays(),
                base.terrain(),
                base.stageReqs(),
                base.feedback(),
                base.players(),
                base.planBreaches());
    }

    static GraphFacts feedback(GraphFacts base, GraphFeedback known) {
        return new GraphFacts(
                base.spirits(),
                base.threats(),
                base.chests(),
                base.resources(),
                base.stations(),
                base.buildings(),
                base.strays(),
                base.terrain(),
                base.stageReqs(),
                known,
                base.players(),
                base.planBreaches());
    }

    static GraphFacts world(GraphFacts base, GraphWorldFacts world) {
        return new GraphFacts(
                base.spirits(),
                base.threats(),
                base.chests(),
                base.resources(),
                base.stations(),
                world.buildings(),
                world.strays(),
                world.terrain(),
                base.stageReqs(),
                base.feedback(),
                base.players(),
                base.planBreaches());
    }

    static GraphFacts players(GraphFacts base, List<GraphFacts.Player> known) {
        return new GraphFacts(
                base.spirits(),
                base.threats(),
                base.chests(),
                base.resources(),
                base.stations(),
                base.buildings(),
                base.strays(),
                base.terrain(),
                base.stageReqs(),
                base.feedback(),
                known,
                base.planBreaches());
    }

    static GraphFacts planBreaches(GraphFacts base, List<GraphFacts.Position> known) {
        return new GraphFacts(
                base.spirits(),
                base.threats(),
                base.chests(),
                base.resources(),
                base.stations(),
                base.buildings(),
                base.strays(),
                base.terrain(),
                base.stageReqs(),
                base.feedback(),
                base.players(),
                known);
    }
}
