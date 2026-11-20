package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class GraphPlanner {
    static final String BUILDING = "building:";
    static final String TOOL = "tool:";
    static final String EVENT = "event:";
    static final String EXPLORE = "explore:";
    static final String STATION = "station:";
    private static final String ORDER = "order:";
    static final char AT = '@';
    final Map<String, GraphPlan.Notice> notices = new LinkedHashMap<>();
    private final SupplyGraph graph;
    private final GraphState state;
    private final GraphReach reach;
    private final GraphFlow flow = new GraphFlow();
    private final GraphChoiceRules choices;
    private final GraphBlocks blockRules;
    private final GraphExpander expander;
    private final Set<String> chances = new HashSet<>();
    private final List<GraphPlan.Advice> advice = new ArrayList<>();
    private final GraphTaskBuilder builder;

    private GraphPlanner(SupplyGraph graph, GraphState state) {
        this.graph = Objects.requireNonNull(graph, "Граф не может быть null: без него не из чего выводить задания");
        this.state = Objects.requireNonNull(state, "Состояние не может быть null: задания выводятся из нехваток");
        this.reach = new GraphReach(graph, state);
        this.choices = new GraphChoiceRules(graph, state, reach);
        this.expander = new GraphExpander(graph, state, reach, flow, new HashSet<>(), chances, notices);
        this.blockRules = new GraphBlocks(graph, state, flow, expander::push, notices);
        this.builder = new GraphTaskBuilder(graph, state, reach, choices, chances);
    }

    public static GraphPlan plan(SupplyGraph graph, GraphState state) {
        GraphPlanner planner = new GraphPlanner(graph, state);
        planner.roots();
        planner.events();
        planner.orders();
        planner.blockRules.apply();
        GraphStanding.collect(graph, state, planner.advice);
        return planner.result(planner.edges());
    }

    public static GraphFlow flow(SupplyGraph graph, GraphState state) {
        GraphPlanner planner = new GraphPlanner(graph, state);
        planner.roots();
        planner.events();
        planner.orders();
        planner.blockRules.apply();
        planner.edges();
        return planner.flow;
    }

    private void roots() {
        for (SupplyGraph.Item item : graph.items().values()) {
            if (item.stage() > state.stage()) {
                continue;
            }
            int have = count(item.id());
            if (have < GraphLack.threshold(item, state) && reach.sourced(item.id())) {
                expander.push(null, item.id(), item.base() + (have < item.xmin() ? graph.urgentBoost() : 0));
            }
        }
        for (String spirit : state.kits().keySet()) {
            for (String kind : graph.kit().kinds()) {
                int target = graph.rank(graph.kit().tier(kind, state.stage()));
                int tier = state.tier(spirit, kind);
                if (tier < target) {
                    double base = state.bestTier(kind) >= target
                            ? graph.kit().secondaryBase()
                            : graph.kit().base();
                    expander.push(null, TOOL + kind + AT + spirit, base + (tier < 0 ? graph.urgentBoost() : 0));
                }
            }
        }
        for (SupplyGraph.Building building : graph.buildings().values()) {
            if (building.stage() <= state.stage()
                    && state.built(building.id()) != Built.BUILT
                    && (!building.optional() || state.built(building.id()) == Built.PLANNED)) {
                expander.requireBuilding(null, building.id(), building.base(), "постройка этапа " + building.stage());
            }
        }
        for (SupplyGraph.Trigger trigger : graph.triggers()) {
            SupplyGraph.Building wanted = graph.buildings().get(trigger.build());
            if (wanted != null && count(trigger.have()) > 0 && state.built(trigger.build()) != Built.BUILT) {
                expander.requireBuilding(
                        null, trigger.build(), GraphLack.base(graph, state, wanted), "есть " + trigger.have());
            }
        }
        if (state.storageFreePercent() < graph.storage().minFreePercent()
                && state.built(graph.storage().building()) == Built.BUILT) {
            notices.putIfAbsent(
                    graph.storage().building(),
                    new GraphPlan.Notice(
                            graph.storage().building(),
                            "склад построен, но свободно " + state.storageFreePercent()
                                    + "%: нужен ещё сундук или расширение"));
        } else if (state.storageFreePercent() < graph.storage().minFreePercent()) {
            expander.requireBuilding(
                    null,
                    graph.storage().building(),
                    graph.storage().base(),
                    "на складе свободно " + state.storageFreePercent() + "% (< "
                            + graph.storage().minFreePercent() + "%)");
        }
        GraphStore.roots(graph, state, expander::push);
        DigOperations.roots(graph, state, expander::push);
    }

    private void events() {
        for (String name : state.events()) {
            GraphEvent event = graph.events().get(name);
            if (event == null) {
                continue;
            }
            for (GraphEvent.Task task :
                    GraphEvent.tasks(name, event, state.facts().leases())) {
                String key = EVENT + name + ":" + task.id();
                flow.root(key, GraphEventFacts.weight(graph, state, task));
                for (Map.Entry<String, Integer> input :
                        GraphEventFacts.consumes(state, task).entrySet()) {
                    if (count(input.getKey()) < input.getValue()) {
                        expander.push(key, input.getKey(), graph.decay());
                    }
                }
            }
            event.push().forEach((node, weight) -> boost(node, weight, name));
            event.advice().forEach(rule -> advice.add(new GraphPlan.Advice(name, rule)));
            VitalPaths.note(graph, state, name, event, notices);
        }
    }

    private void orders() {
        for (Map.Entry<String, Integer> lack : state.orders().entrySet()) {
            String item = lack.getKey();
            if (graph.items().containsKey(item) || graph.isGroup(item)) {
                expander.push(null, item, graph.maxPriority());
            } else {
                notices.putIfAbsent(
                        ORDER + item,
                        new GraphPlan.Notice(
                                item,
                                "заказ игрока ждёт «" + item + "» ×" + lack.getValue()
                                        + ", а граф не знает, как его добыть: добавьте предмет в граф"));
            }
        }
    }

    private void boost(String node, int weight, String event) {
        if (node.startsWith(TOOL)) {
            String kind = node.substring(TOOL.length());
            int target = graph.rank(graph.kit().tier(kind, state.stage()));
            for (String spirit : state.kits().keySet()) {
                if (state.tier(spirit, kind) < target) {
                    expander.push(null, TOOL + kind + AT + spirit, weight);
                }
            }
        } else if (node.startsWith(BUILDING)) {
            String id = node.substring(BUILDING.length());
            if (state.built(id) != Built.BUILT) {
                expander.requireBuilding(null, id, weight, "событие " + event);
            }
        } else if (GraphLack.lacking(graph, state, node)) {
            expander.push(null, node, weight);
        }
    }

    private Map<String, Double> edges() {
        Map<String, Double> scores = flow.solve();
        boolean changed = false;
        for (SupplyGraph.Edge edge : graph.edges()) {
            if (!GraphLack.lacking(graph, state, edge.lack())) {
                continue;
            }
            boolean active = false;
            for (String key : List.copyOf(scores.keySet())) {
                if ((key.equals(edge.way()) || key.startsWith(edge.way() + AT)) && scores.getOrDefault(key, 0.0) > 0) {
                    flow.local(key, GraphPriorities.edge(graph, scores.getOrDefault(key, 0.0), edge.weight()));
                    active = true;
                }
            }
            SupplyGraph.Way way = reach.way(edge.way()).orElse(null);
            if (!active && edge.weight() > 0 && way != null && !graph.isTool(way.makes()) && reach.available(way)) {
                flow.root(way.id(), edge.weight());
                expander.expandWay(way.id(), way);
                changed = true;
            } else if (active) {
                changed = true;
            }
        }
        return changed ? flow.solve() : scores;
    }

    private GraphPlan result(Map<String, Double> scores) {
        List<GraphPlan.Task> tasks = new ArrayList<>();
        Map<String, Double> load = new LinkedHashMap<>();
        Set<String> made = new HashSet<>();
        for (Map.Entry<String, Double> score : GraphChoiceRules.ordered(scores)) {
            if (score.getValue() <= 0 || !builder.task(score.getKey())) {
                continue;
            }
            boolean repeat = !made.add(choices.makes(score.getKey()));
            double value = score.getValue() * graph.costs().travelFactor(choices.travelTicks(score.getKey()));
            double adjusted =
                    choices.value(score.getKey(), value, repeat) * BuildingShare.of(graph, state, score.getKey());
            GraphPlan.Task task =
                    builder.task(score.getKey(), GraphEvent.priority(state, graph, score.getKey(), adjusted));
            String reason = blockRules.reason(score.getKey());
            if (reason != null) {
                task = GraphBlocks.blockedTask(task, reason);
            }
            GraphPlan.Task ready = task;
            tasks.add(ready);
            builder.station(score.getKey())
                    .filter(ignored -> ready.feasible())
                    .ifPresent(building -> load.merge(building, adjusted, Math::max));
        }
        load.forEach((building, value) ->
                builder.stationTask(building, value, scores).ifPresent(tasks::add));
        return new GraphPlan(tasks, List.copyOf(notices.values()), advice);
    }

    private int count(String node) {
        return GraphLack.count(graph, state, node);
    }
}
