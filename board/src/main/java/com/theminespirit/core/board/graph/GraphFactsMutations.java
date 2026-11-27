package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.audit.Group;
import java.util.List;
import java.util.Map;

public interface GraphFactsMutations {
    default GraphFacts withAuditGroups(List<Group> known) {
        return GraphFactsCopies.auditGroups((GraphFacts) this, known);
    }

    default GraphFacts withChests(List<GraphFacts.Chest> known) {
        return GraphFactsCopies.chests((GraphFacts) this, known);
    }

    default GraphFacts withThreats(List<GraphFacts.Threat> known) {
        return GraphFactsCopies.threats((GraphFacts) this, known);
    }

    default GraphFacts withSpirits(Map<String, GraphFacts.Spirit> known) {
        return GraphFactsCopies.spirits((GraphFacts) this, known);
    }

    default GraphFacts withFeedback(GraphFeedback known) {
        return GraphFactsCopies.feedback((GraphFacts) this, known);
    }

    default GraphFacts withWorld(GraphWorldFacts world) {
        return GraphFactsCopies.world((GraphFacts) this, world);
    }

    default GraphFacts withPlayers(List<GraphFacts.Player> known) {
        return GraphFactsCopies.players((GraphFacts) this, known);
    }

    default GraphFacts withPlanBreaches(List<GraphFacts.Position> known) {
        return GraphFactsCopies.planBreaches((GraphFacts) this, known);
    }
}
