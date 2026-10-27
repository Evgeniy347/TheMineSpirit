package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class GraphPlanner {
    private static final String BUILDING = "building:";
    private static final String TOOL = "tool:";
    private static final String EVENT = "event:";
    private static final char AT = '@';

    private final SupplyGraph graph;
    private final GraphState state;
    private final Map<String, Double> scores = new LinkedHashMap<>();
    private final Map<String, GraphPlan.Notice> notices = new LinkedHashMap<>();
    private final Set<String> chances = new HashSet<>();
    private final List<GraphPlan.Advice> advice = new ArrayList<>();

    private GraphPlanner(SupplyGraph graph, GraphState state) {
        this.graph = Objects.requireNonNull(graph, "Граф не может быть null: без него не из чего выводить задания");
        this.state = Objects.requireNonNull(state, "Состояние не может быть null: задания выводятся из нехваток");
    }

    public static GraphPlan plan(SupplyGraph graph, GraphState state) {
        GraphPlanner planner = new GraphPlanner(graph, state);
        planner.roots();
        planner.events();
        planner.edges();
        return planner.result();
    }

    public static int threshold(SupplyGraph.Item item, GraphState state) {
        return (int) Math.ceil(item.xmin() * state.spirits() * state.stage() * item.k());
    }

    public static boolean lacking(SupplyGraph graph, GraphState state, String node) {
        SupplyGraph.Item item = graph.items().get(node);
        if (item != null && item.stage() <= state.stage()) {
            return state.count(node) < threshold(item, state);
        }
        if (graph.isTool(node)) {
            int split = node.indexOf('_');
            return state.bestTier(node.substring(split + 1)) < graph.rank(node.substring(0, split));
        }
        return false;
    }

    private void roots() {
        for (SupplyGraph.Item item : graph.items().values()) {
            if (item.stage() > state.stage()) {
                continue;
            }
            int have = state.count(item.id());
            if (have < threshold(item, state)) {
                push(item.id(), item.base() + (have < item.xmin() ? graph.urgentBoost() : 0), new HashSet<>());
            }
        }
        for (String spirit : state.kits().keySet()) {
            for (String kind : graph.kit().kinds()) {
                int target = graph.rank(graph.kit().tier(kind, state.stage()));
                int tier = state.tier(spirit, kind);
                if (tier < target) {
                    push(
                            toolKey(kind, spirit),
                            graph.kit().base() + (tier < 0 ? graph.urgentBoost() : 0),
                            new HashSet<>());
                }
            }
        }
        for (SupplyGraph.Building building : graph.buildings().values()) {
            if (building.stage() <= state.stage() && state.built(building.id()) != Built.BUILT) {
                requireBuilding(building.id(), building.base(), "постройка этапа " + building.stage(), new HashSet<>());
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
            requireBuilding(
                    graph.storage().building(),
                    graph.storage().base(),
                    "на складе свободно " + state.storageFreePercent() + "% (< "
                            + graph.storage().minFreePercent() + "%)",
                    new HashSet<>());
        }
    }

    private void push(String node, double value, Set<String> path) {
        if (value == 0 || !path.add(node)) {
            return;
        }
        if (node.startsWith(TOOL)) {
            String kind = node.substring(TOOL.length(), node.indexOf(AT));
            String spirit = node.substring(node.indexOf(AT) + 1);
            for (String tier : graph.tiers()) {
                if (graph.rank(tier) <= state.tier(spirit, kind)) {
                    continue;
                }
                boolean aboveKit = graph.rank(tier) > graph.rank(graph.kit().tier(kind, state.stage()));
                for (SupplyGraph.Way way : graph.producers(tier + "_" + kind)) {
                    if (!aboveKit) {
                        activate(way, way.id() + AT + spirit, value * way.weight(), path);
                    } else {
                        chances.add(way.id() + AT + spirit);
                        add(way.id() + AT + spirit, value * way.weight());
                    }
                }
            }
        } else if (node.startsWith(BUILDING)) {
            String id = node.substring(BUILDING.length());
            add(node, value);
            SupplyGraph.Building building = graph.buildings().get(id);
            Map<String, Integer> needs = materials(id);
            if (building != null
                    && stocked(needs)
                    && (building.tool() == null || state.bestTier(building.tool()) >= 0)) {
                add(node, graph.buildReadyBoost());
            }
            for (Map.Entry<String, Integer> input : materials(id).entrySet()) {
                if (state.count(input.getKey()) < input.getValue()) {
                    push(input.getKey(), value * graph.decay(), path);
                }
            }
            if (building != null && building.tool() != null && state.bestTier(building.tool()) < 0) {
                pushTool(building.tool(), value * graph.decay(), path);
            }
        } else {
            for (SupplyGraph.Way way : graph.producers(node)) {
                activate(way, way.id(), value * way.weight(), path);
            }
        }
        path.remove(node);
    }

    private void activate(SupplyGraph.Way way, String key, double value, Set<String> path) {
        add(key, value);
        double down = value * graph.decay();
        for (Map.Entry<String, Integer> input : way.consumes().entrySet()) {
            if (state.count(input.getKey()) < input.getValue()) {
                push(input.getKey(), down, path);
            }
        }
        if (way.tool() != null && state.bestTier(way.tool()) < graph.rank(way.tier() == null ? "wooden" : way.tier())) {
            pushTool(way.tool(), down, path);
        }
        if (way.building() != null && state.built(way.building()) != Built.BUILT) {
            requireBuilding(way.building(), down, "нужна пути " + way.id(), path);
        }
    }

    private void pushTool(String kind, double value, Set<String> path) {
        for (String spirit : state.kits().keySet()) {
            push(toolKey(kind, spirit), value / Math.max(1, state.spirits()), path);
        }
    }

    private void requireBuilding(String id, double value, String why, Set<String> path) {
        SupplyGraph.Building building = graph.buildings().get(id);
        if (building == null || state.built(id) == Built.ABSENT || building.stage() > state.stage()) {
            notices.putIfAbsent(
                    id,
                    new GraphPlan.Notice(
                            id,
                            "нужна постройка " + id + " (" + why + "), её нет в плане"
                                    + (building != null && building.stage() > state.stage()
                                            ? " этапа " + state.stage()
                                            : "")));
            return;
        }
        push(BUILDING + id, value, path);
    }

    private void events() {
        for (String name : state.events()) {
            GraphEvent event = graph.events().get(name);
            if (event == null) {
                continue;
            }
            for (GraphEvent.Task task : event.tasks()) {
                String key = EVENT + name + ":" + task.id();
                add(key, task.base());
                for (Map.Entry<String, Integer> input : task.consumes().entrySet()) {
                    if (state.count(input.getKey()) < input.getValue()) {
                        push(input.getKey(), task.base() * graph.decay(), new HashSet<>());
                    }
                }
            }
            event.push().forEach((node, weight) -> {
                if (node.startsWith(TOOL)) {
                    pushTool(node.substring(TOOL.length()), weight, new HashSet<>());
                } else if (node.startsWith(BUILDING)) {
                    requireBuilding(node.substring(BUILDING.length()), weight, "событие " + name, new HashSet<>());
                } else {
                    push(node, weight, new HashSet<>());
                }
            });
            event.advice().forEach(rule -> advice.add(new GraphPlan.Advice(name, rule)));
        }
    }

    private void edges() {
        for (SupplyGraph.Edge edge : graph.edges()) {
            if (!lacking(graph, state, edge.lack())) {
                continue;
            }
            for (String key : List.copyOf(scores.keySet())) {
                if (key.equals(edge.way()) || key.startsWith(edge.way() + AT)) {
                    add(key, edge.weight());
                }
            }
        }
    }

    private void add(String key, double value) {
        double top = graph.maxPriority();
        double bounded = Math.max(-top, Math.min(top, value));
        scores.merge(
                key, bounded, (before, more) -> more < 0 ? before + more : top - (top - before) * (top - more) / top);
    }

    private GraphPlan result() {
        List<GraphPlan.Task> tasks = new ArrayList<>();
        for (Map.Entry<String, Double> score : scores.entrySet()) {
            int priority = (int) Math.max(0, Math.min(graph.maxPriority(), Math.round(score.getValue())));
            tasks.add(task(score.getKey(), priority));
        }
        return new GraphPlan(tasks, List.copyOf(notices.values()), advice);
    }

    private GraphPlan.Task task(String key, int priority) {
        if (key.startsWith(EVENT)) {
            String[] parts = key.split(":", 3);
            GraphEvent.Task event = Objects.requireNonNull(
                            graph.events().get(parts[1]), "Событие " + parts[1] + " задания не описано в графе")
                    .tasks()
                    .stream()
                    .filter(task -> task.id().equals(parts[2]))
                    .findFirst()
                    .orElseThrow();
            return new GraphPlan.Task(
                    key,
                    event.boardCase(),
                    event.id(),
                    Optional.empty(),
                    priority,
                    event.consumes(),
                    stocked(event.consumes()),
                    false,
                    true);
        }
        if (key.startsWith(BUILDING)) {
            String id = key.substring(BUILDING.length());
            SupplyGraph.Building building = Objects.requireNonNull(
                    graph.buildings().get(id), "Постройка " + id + " задания не описана в графе");
            Map<String, Integer> materials = materials(id);
            boolean tooled = building.tool() == null || state.bestTier(building.tool()) >= 0;
            return new GraphPlan.Task(
                    key,
                    building.boardCase(),
                    id,
                    Optional.empty(),
                    priority,
                    materials,
                    tooled && stocked(materials),
                    false,
                    graph.implemented(id));
        }
        int at = key.indexOf(AT);
        String wayId = at < 0 ? key : key.substring(0, at);
        SupplyGraph.Way way = graph.way(wayId).orElseThrow();
        Optional<String> spirit = at < 0 ? Optional.empty() : Optional.of(key.substring(at + 1));
        boolean tooled = way.tool() == null
                || state.bestTier(way.tool()) >= graph.rank(way.tier() == null ? "wooden" : way.tier());
        return new GraphPlan.Task(
                key,
                way.boardCase(),
                way.makes(),
                spirit,
                priority,
                way.consumes(),
                tooled && housed(way) && stocked(way.consumes()),
                chances.contains(key),
                graph.implemented(wayId));
    }

    private Map<String, Integer> materials(String building) {
        SupplyGraph.Building known = graph.buildings().get(building);
        return state.materials().getOrDefault(building, known == null ? Map.of() : known.materials());
    }

    private boolean housed(SupplyGraph.Way way) {
        return way.building() == null || state.built(way.building()) == Built.BUILT;
    }

    private boolean stocked(Map<String, Integer> needs) {
        return needs.entrySet().stream().allMatch(entry -> state.count(entry.getKey()) >= entry.getValue());
    }

    private static String toolKey(String kind, String spirit) {
        return TOOL + kind + AT + spirit;
    }
}
