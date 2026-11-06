package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.graph.GraphDecisions;
import com.theminespirit.core.board.graph.GraphExplain;
import com.theminespirit.core.board.graph.GraphFailureText;
import com.theminespirit.core.board.graph.GraphNotes;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphState;
import org.slf4j.Logger;

final class GraphDecisionNote {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final GraphNotes notes = new GraphNotes();
    private GraphDecisions decisions = GraphDecisions.empty();

    void note(GraphPlan plan, long now) {
        decisions = GraphExplain.of(plan, now);
        if (!notes.due("decisions", now)) {
            return;
        }
        for (GraphDecisions.Row row : decisions.decisions()) {
            LOGGER.info("[graph] решение: {}", row.line());
        }
    }

    void noteFailures(GraphState state, long now) {
        if (!notes.due("failures", now)) {
            return;
        }
        String line = GraphFailureText.line(state.facts().feedback().failures());
        if (!line.isEmpty()) {
            LOGGER.info("[graph] память неудач: {}", line);
        }
    }

    GraphDecisions decisions() {
        return decisions;
    }
}
