package com.theminespirit.core.board.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
        List<Ticket> posted = new ArrayList<>();
        Set<String> live = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        List<GraphPlan.Task> unimplemented = new ArrayList<>();
        Map<String, Integer> demand = buildingDemand(graph, plan);
        for (GraphPlan.Task task : plan.tasks()) {
            if (!task.implemented()) {
                unimplemented.add(task);
                continue;
            }
            if (!task.raised() || early(graph, state, task)) {
                continue;
            }
            live.add(id(task));
            if (!task.feasible()) {
                continue;
            }
            if (!seen.add(id(task))) {
                continue;
            }
            Kind kind = kind(task);
            String node = kind == Kind.WAY ? wayId(task) : task.makes();
            int count = kind == Kind.WAY ? count(graph, state, task, demand) : 1;
            posted.add(new Ticket(id(task), kind, node, task, count));
        }
        List<Ticket> post = consolidate(posted);
        post.sort(Comparator.comparingInt((Ticket ticket) -> ticket.task().priority())
                .reversed()
                .thenComparing(Ticket::id));
        return new Result(post, live, unimplemented);
    }

    private static List<Ticket> consolidate(List<Ticket> tickets) {
        Map<String, Ticket> best = new LinkedHashMap<>();
        for (Ticket ticket : tickets) {
            String key = ticket.kind() == Kind.EVENT || ticket.task().spirit().isPresent()
                    ? "id:" + ticket.id()
                    : ticket.kind() + ":" + ticket.task().makes();
            Ticket before = best.get(key);
            if (before == null || stronger(ticket, before)) {
                best.put(key, ticket);
            }
        }
        return new ArrayList<>(best.values());
    }

    private static boolean stronger(Ticket candidate, Ticket current) {
        if (candidate.task().priority() != current.task().priority()) {
            return candidate.task().priority() > current.task().priority();
        }
        if (candidate.count() != current.count()) {
            return candidate.count() > current.count();
        }
        return candidate.id().compareTo(current.id()) < 0;
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

    private static int count(SupplyGraph graph, GraphState state, GraphPlan.Task task, Map<String, Integer> demand) {
        SupplyGraph.Item item = graph.items().get(task.makes());
        if (item == null) {
            return 1;
        }
        int yield = graph.way(wayId(task)).map(SupplyGraph.Way::yield).orElse(1);
        int have = state.count(item.id());
        int shortage = Math.max(
                Math.max(GraphLack.threshold(item, state), demand.getOrDefault(item.id(), 0)) - have,
                state.orders().getOrDefault(item.id(), 0));
        return Math.max(1, Math.min(COUNT_MAX, Math.max(yield, shortage)));
    }

    private static Map<String, Integer> buildingDemand(SupplyGraph graph, GraphPlan plan) {
        Map<String, Integer> demand = new LinkedHashMap<>();
        for (GraphPlan.Task task : plan.tasks()) {
            if (!task.id().startsWith(BUILDING)) {
                continue;
            }
            task.consumes().forEach((item, count) -> {
                if (graph.isGroup(item)) {
                    graph.members(item).forEach(member -> demand.merge(member, count, Integer::sum));
                } else {
                    demand.merge(item, count, Integer::sum);
                }
            });
        }
        return demand;
    }
}
