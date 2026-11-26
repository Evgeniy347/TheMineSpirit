package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.VitalGate;
import com.theminespirit.core.board.graph.GraphDecisions;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.log.Perf;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class GraphReport {
    private final GraphSource.Half half;
    private final List<String> unconnected;
    private int step;

    GraphReport(GraphSource.Half half, List<String> unconnected) {
        this.half = half;
        this.unconnected = List.copyOf(unconnected);
    }

    void advance(GraphReporter reporter, GraphDecisionNote decisions) {
        long started = System.nanoTime();
        switch (step) {
            case 0 -> reporter.report(half.plan(), half.result(), unconnected, freeSpirit(half), half.now());
            case 1 -> decisions.note(half.plan(), half.now(), vitals(half));
            case 2 -> decisions.noteFailures(half.state(), half.now());
            case 3 -> reporter.world(half.level(), half.state(), half.now());
            case 4 ->
                reporter.builds(half.level(), half.service(), half.graph(), half.state(), half.scan(), half.now());
            case 5 -> {
                if (half.deep()) {
                    reporter.deep(half);
                }
            }
            case 6 -> reporter.strategist(half.level(), half.graph(), half.state(), half.plan(), half.now());
            default -> throw new IllegalStateException("Шаг отчёта графа " + step + " не описан");
        }
        Perf.record("graph.src.report." + step, System.nanoTime() - started);
        step++;
    }

    boolean done() {
        return step > 6;
    }

    private static List<GraphDecisions.Vital> vitals(GraphSource.Half half) {
        List<GraphDecisions.Vital> rows = new ArrayList<>();
        for (SpiritEntity spirit : half.spirits()) {
            if (spirit.isRemoved()) {
                continue;
            }
            Map<String, String> needs = SpiritVitals.needs(spirit);
            if (needs.isEmpty()) {
                continue;
            }
            String id = spirit.getUUID().toString();
            String doing = half.service().board().assignedTo(spirit.getUUID()).stream()
                    .filter(ticket -> VitalGate.blocksWork(ticket, needs))
                    .map(ticket -> ticket.id())
                    .findFirst()
                    .orElse("");
            String task = half.plan().tasks().stream()
                    .filter(candidate -> candidate.spirit().filter(id::equals).isPresent())
                    .map(candidate -> GraphTickets.PREFIX + candidate.id())
                    .filter(ticket -> VitalGate.satisfies(ticket, needs))
                    .findFirst()
                    .orElse("");
            needs.forEach((need, reason) -> rows.add(new GraphDecisions.Vital(id, need, reason, task, doing)));
        }
        return List.copyOf(rows);
    }

    private static boolean freeSpirit(GraphSource.Half half) {
        return half.spirits().stream()
                .anyMatch(spirit -> !spirit.isRemoved()
                        && half.service().board().assignedTo(spirit.getUUID()).isEmpty()
                        && half.service().board().occupation(spirit.getUUID()).isEmpty());
    }
}
