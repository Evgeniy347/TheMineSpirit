package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.graph.GraphDecisions;
import com.theminespirit.core.board.graph.GraphExplain;
import com.theminespirit.core.board.graph.GraphFailureText;
import com.theminespirit.core.board.graph.GraphNotes;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.neoforge.log.SpiritWorldLog;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;

final class GraphDecisionNote {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final GraphNotes notes = new GraphNotes();
    private GraphDecisions decisions = GraphDecisions.empty();
    private String lastSnapshot = "";

    void note(GraphPlan plan, long now, List<GraphDecisions.Vital> vital) {
        decisions = new GraphDecisions(now, GraphExplain.of(plan, now).decisions(), vital);
        String snapshot =
                decisions.decisions().stream().map(GraphDecisions.Row::line).collect(Collectors.joining("\n"));
        if (!snapshot.equals(lastSnapshot)) {
            lastSnapshot = snapshot;
            for (GraphDecisions.Row row : decisions.decisions()) {
                SpiritWorldLog.graphDecision(
                        row.task(), row.spirit(), "", row.weight(), row.feasible(), row.next(), row.why(), now);
            }
        }
        if (!notes.due("decisions", now)) {
            return;
        }
        for (GraphDecisions.Row row : decisions.decisions()) {
            LOGGER.info("[graph] решение: {}", row.line());
        }
        vitalLine(vital);
    }

    private void vitalLine(List<GraphDecisions.Vital> vital) {
        List<GraphDecisions.Vital> broken =
                vital.stream().filter(row -> !row.doing().isBlank()).toList();
        if (vital.isEmpty()) {
            return;
        }
        String needs = vital.stream()
                .map(row -> row.spirit() + " — " + row.reason() + (row.task().isBlank() ? "" : " (" + row.task() + ")"))
                .toList()
                .toString();
        if (broken.isEmpty()) {
            LOGGER.info("[graph] нужды живы, обычной работы при них нет: {}", needs);
            return;
        }
        LOGGER.warn(
                "[graph] нужда жива, а дух делает не-жизненную работу: {}; нужды: {}",
                broken.stream()
                        .map(row -> row.spirit() + " — " + row.need() + " вместо " + row.doing())
                        .toList(),
                needs);
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
