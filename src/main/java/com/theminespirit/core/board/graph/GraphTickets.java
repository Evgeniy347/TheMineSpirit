package com.theminespirit.core.board.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class GraphTickets {
    public static final String PREFIX = "graph:";
    public static final int COUNT_MAX = 64;
    private static final String BUILDING = "building:";
    private static final String EVENT = "event:";

    private GraphTickets() {}

    public enum Kind {
        WAY,
        BUILDING,
        EVENT
    }

    public record Ticket(String id, Kind kind, String node, GraphPlan.Task task, int count) {
        public Ticket {
            Objects.requireNonNull(task, "Задание графа не может быть null: из него берут кейс и приоритет");
            if (count < 1) {
                throw new IllegalArgumentException("Задание " + id + " на " + count + " штук: делать нечего");
            }
        }
    }

    public record Result(List<Ticket> post, Set<String> live, List<GraphPlan.Task> unimplemented) {
        public Result {
            post = List.copyOf(post);
            live = Set.copyOf(live);
            unimplemented = List.copyOf(unimplemented);
        }
    }

    public static String id(GraphPlan.Task task) {
        return PREFIX + task.id();
    }

    public static Result of(SupplyGraph graph, GraphState state, GraphPlan plan) {
        Objects.requireNonNull(graph, "Граф не может быть null: по нему считают число добычи");
        Objects.requireNonNull(state, "Состояние не может быть null: по нему считают нехватку");
        Objects.requireNonNull(plan, "Расчёт графа не может быть null: из него ставятся задания доски");
        List<Ticket> post = new ArrayList<>();
        Set<String> live = new LinkedHashSet<>();
        List<GraphPlan.Task> unimplemented = new ArrayList<>();
        for (GraphPlan.Task task : plan.tasks()) {
            if (!task.implemented()) {
                unimplemented.add(task);
                continue;
            }
            if (task.priority() <= 0 || early(graph, state, task)) {
                continue;
            }
            live.add(id(task));
            if (!task.feasible()) {
                continue;
            }
            Kind kind = kind(task);
            String node = kind == Kind.WAY ? wayId(task) : task.makes();
            int count = kind == Kind.WAY ? count(graph, state, task) : 1;
            post.add(new Ticket(id(task), kind, node, task, count));
        }
        post.sort(Comparator.comparingInt((Ticket ticket) -> ticket.task().priority())
                .reversed()
                .thenComparing(Ticket::id));
        return new Result(post, live, unimplemented);
    }

    private static boolean early(SupplyGraph graph, GraphState state, GraphPlan.Task task) {
        SupplyGraph.Item item = graph.items().get(task.makes());
        return kind(task) == Kind.WAY && item != null && item.stage() > state.stage();
    }

    private static Kind kind(GraphPlan.Task task) {
        if (task.id().startsWith(BUILDING)) {
            return Kind.BUILDING;
        }
        return task.id().startsWith(EVENT) ? Kind.EVENT : Kind.WAY;
    }

    private static String wayId(GraphPlan.Task task) {
        int at = task.id().indexOf('@');
        return at < 0 ? task.id() : task.id().substring(0, at);
    }

    private static int count(SupplyGraph graph, GraphState state, GraphPlan.Task task) {
        SupplyGraph.Item item = graph.items().get(task.makes());
        if (item == null) {
            return 1;
        }
        int yield = graph.way(wayId(task)).map(SupplyGraph.Way::yield).orElse(1);
        int shortage = GraphPlanner.threshold(item, state) - state.count(item.id());
        return Math.max(1, Math.min(COUNT_MAX, Math.max(yield, shortage)));
    }
}
