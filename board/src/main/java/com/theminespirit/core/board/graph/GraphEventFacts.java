package com.theminespirit.core.board.graph;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class GraphEventFacts {
    private GraphEventFacts() {}

    public static Map<String, Integer> consumes(GraphState state, GraphEvent.Task task) {
        Objects.requireNonNull(state, "Состояние графа не может быть null: из него берут материалы ремонта");
        Objects.requireNonNull(task, "Задание события не может быть null: у него есть расход");
        if (task.building() != null) {
            Map<String, Integer> known = state.materials().get(task.building());
            if (known != null) {
                return known;
            }
        }
        return task.consumes();
    }

    public static boolean feasible(SupplyGraph graph, GraphState state, GraphEvent.Task task) {
        if (task.world() != null && !state.world().contains(task.world())) {
            return false;
        }
        if (task.onlyWhenEmpty() != null && GraphLack.count(graph, state, task.onlyWhenEmpty()) > 0) {
            return false;
        }
        return GraphLack.stocked(graph, state, consumes(state, task))
                && (task.building() == null || repairTarget(state, task.building()));
    }

    private static boolean repairTarget(GraphState state, String building) {
        if (state.built(building) == GraphState.Built.BUILT) {
            return true;
        }
        GraphFacts.Building fact = state.facts().buildings().get(building);
        return fact != null && !fact.unknown() && fact.auditPercent() > 0;
    }

    public static boolean applies(SupplyGraph graph, GraphState state, GraphEvent.Task task) {
        if (task.world() != null && !state.world().contains(task.world())) {
            return false;
        }
        return task.onlyWhenEmpty() == null || GraphLack.count(graph, state, task.onlyWhenEmpty()) == 0;
    }

    public static boolean needEvent(GraphEvent event) {
        return event.tasks().stream().anyMatch(candidate -> candidate.onlyWhenEmpty() != null);
    }

    public static boolean need(SupplyGraph graph, GraphState state, GraphEvent event, GraphEvent.Task task) {
        return needEvent(event) && feasible(graph, state, task);
    }

    public static Optional<String> owner(GraphState state, GraphEvent.Task task) {
        if (!"hungriest".equals(task.owner())) {
            return Optional.empty();
        }
        return state.facts().spirits().values().stream()
                .min(Comparator.comparingInt(GraphFacts.Spirit::food).thenComparing(GraphFacts.Spirit::id))
                .map(GraphFacts.Spirit::id);
    }

    public static int weight(SupplyGraph graph, GraphState state, GraphEvent.Task task) {
        Objects.requireNonNull(state, "Состояние графа не может быть null: из него берут долю недостающего");
        Objects.requireNonNull(task, "Задание события не может быть null: у него есть вес");
        if (!applies(graph, state, task)) {
            return 0;
        }
        if (task.building() == null) {
            return task.base();
        }
        GraphFacts.Building fact = state.facts().buildings().get(task.building());
        if (fact == null || fact.unknown()) {
            return task.base();
        }
        double share = (100 - fact.auditPercent()) / 100.0;
        double importance = state.facts().stageReqs().minimum().containsKey(task.building()) ? 1.5 : 1.0;
        return task.base() + (int) Math.round(task.base() * share * importance);
    }
}
