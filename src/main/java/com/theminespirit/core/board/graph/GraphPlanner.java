package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.BoardCase;
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
    private static final String EXPLORE = "explore:";
    private static final char AT = '@';
    private static final double NEGLIGIBLE = 0.5;

    private final SupplyGraph graph;
    private final GraphState state;
    private final Map<String, Double> scores = new LinkedHashMap<>();
    private final Map<String, GraphPlan.Notice> notices = new LinkedHashMap<>();
    private final Set<String> chances = new HashSet<>();
    private final List<GraphPlan.Advice> advice = new ArrayList<>();
    private int pushes;

    private GraphPlanner(SupplyGraph graph, GraphState state) {
        this.graph = Objects.requireNonNull(graph, "Граф не может быть null: без него не из чего выводить задания");
        this.state = Objects.requireNonNull(state, "Состояние не может быть null: задания выводятся из нехваток");
    }

    public static GraphPlan plan(SupplyGraph graph, GraphState state) {
        GraphPlanner planner = new GraphPlanner(graph, state);
        planner.roots();
        planner.events();
        planner.standing();
        planner.edges();
        return planner.result();
    }

    public static int threshold(SupplyGraph.Item item, GraphState state) {
        int stage = item.perSpirit() ? 1 : state.stage();
        return (int) Math.ceil(item.xmin() * state.spirits() * stage * item.k());
    }

    public static boolean lacking(SupplyGraph graph, GraphState state, String node) {
        if (graph.isGroup(node)) {
            List<String> members = graph.members(node);
            SupplyGraph.Item first = graph.items().get(members.get(0));
            int have = members.stream().mapToInt(state::count).sum();
            return first != null && have < threshold(first, state);
        }
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
            int have = count(item.id());
            if (have < threshold(item, state) && sourced(item.id())) {
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
                            (state.bestTier(kind) >= target
                                            ? graph.kit().secondaryBase()
                                            : graph.kit().base())
                                    + (tier < 0 ? graph.urgentBoost() : 0),
                            new HashSet<>());
                }
            }
        }
        for (SupplyGraph.Building building : graph.buildings().values()) {
            if (!building.optional()
                    && building.stage() <= state.stage()
                    && state.built(building.id()) != Built.BUILT) {
                requireBuilding(building.id(), building.base(), "постройка этапа " + building.stage(), new HashSet<>());
            }
        }
        graph.herdCaps().forEach((herd, cap) -> {
            if (count(herd) > cap) {
                for (SupplyGraph.Way way : graph.ways()) {
                    if (way.consumes().containsKey(herd) && available(way)) {
                        add(way.id(), graph.herdCullBoost());
                    }
                }
            }
        });
        for (SupplyGraph.Trigger trigger : graph.triggers()) {
            SupplyGraph.Building wanted = graph.buildings().get(trigger.build());
            if (wanted != null && count(trigger.have()) > 0 && state.built(trigger.build()) != Built.BUILT) {
                requireBuilding(trigger.build(), wanted.base(), "есть " + trigger.have(), new HashSet<>());
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
        if (Math.abs(value) < NEGLIGIBLE || !path.add(node)) {
            return;
        }
        pushes++;
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
                    && building.requires().stream().allMatch(required -> state.built(required) == Built.BUILT)
                    && (building.tool() == null || state.bestTier(building.tool()) >= 0)) {
                add(node, graph.buildReadyBoost());
            }
            for (Map.Entry<String, Integer> input : materials(id).entrySet()) {
                if (count(input.getKey()) < input.getValue()) {
                    push(input.getKey(), value * graph.decay(), path);
                }
            }
            if (building != null) {
                for (String required : building.requires()) {
                    if (state.built(required) != Built.BUILT) {
                        requireBuilding(required, value * graph.decay(), "нужна постройке " + id, path);
                    }
                }
            }
            if (building != null && building.tool() != null && state.bestTier(building.tool()) < 0) {
                pushTool(building.tool(), value * graph.decay(), path);
            }
        } else if (graph.isGroup(node)) {
            for (String member : graph.members(node)) {
                if (sourced(member)) {
                    push(member, value, path);
                }
            }
        } else {
            List<SupplyGraph.Way> producers = graph.producers(node);
            for (SupplyGraph.Way way : producers) {
                if (available(way)) {
                    activate(way, way.id(), value * way.weight(), path);
                }
            }
            List<String> missing = producers.stream()
                    .map(SupplyGraph.Way::world)
                    .filter(resource -> resource != null && !state.world().contains(resource))
                    .distinct()
                    .toList();
            if (!missing.isEmpty() && !sourced(node)) {
                notices.putIfAbsent(
                        node,
                        new GraphPlan.Notice(
                                node,
                                "нет источника " + node + " в округе (нет " + missing
                                        + "): разведать или сменить материал/породу постройки"));
                missing.forEach(resource -> add(EXPLORE + resource, value * graph.decay()));
            }
        }
        path.remove(node);
    }

    private void activate(SupplyGraph.Way way, String key, double value, Set<String> path) {
        add(key, value);
        double down = value * graph.decay();
        for (Map.Entry<String, Integer> input : way.consumes().entrySet()) {
            if (count(input.getKey()) < input.getValue()) {
                push(input.getKey(), down, path);
            }
        }
        for (Map.Entry<String, Integer> input : way.needs().entrySet()) {
            if (count(input.getKey()) < input.getValue()) {
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
                    if (count(input.getKey()) < input.getValue()) {
                        push(input.getKey(), task.base() * graph.decay(), new HashSet<>());
                    }
                }
            }
            event.push().forEach((node, weight) -> boost(node, weight, name));
            event.advice().forEach(rule -> advice.add(new GraphPlan.Advice(name, rule)));
        }
    }

    private void standing() {
        for (SupplyGraph.Building building : graph.buildings().values()) {
            if (state.built(building.id()) == Built.BUILT) {
                building.advice().forEach(rule -> advice.add(new GraphPlan.Advice(building.id(), rule)));
            }
        }
    }

    private void boost(String node, int weight, String event) {
        if (node.startsWith(TOOL)) {
            String kind = node.substring(TOOL.length());
            int target = graph.rank(graph.kit().tier(kind, state.stage()));
            for (String spirit : state.kits().keySet()) {
                if (state.tier(spirit, kind) < target) {
                    push(toolKey(kind, spirit), weight, new HashSet<>());
                }
            }
        } else if (node.startsWith(BUILDING)) {
            String id = node.substring(BUILDING.length());
            if (state.built(id) != Built.BUILT) {
                requireBuilding(id, weight, "событие " + event, new HashSet<>());
            }
        } else if (lacking(graph, state, node)) {
            push(node, weight, new HashSet<>());
        }
    }

    private void edges() {
        for (SupplyGraph.Edge edge : graph.edges()) {
            if (!lacking(graph, state, edge.lack())) {
                continue;
            }
            boolean active = false;
            for (String key : List.copyOf(scores.keySet())) {
                if (key.equals(edge.way()) || key.startsWith(edge.way() + AT)) {
                    add(key, edge.weight());
                    active = true;
                }
            }
            SupplyGraph.Way way = graph.way(edge.way()).orElse(null);
            if (!active && edge.weight() > 0 && way != null && !graph.isTool(way.makes()) && available(way)) {
                activate(way, way.id(), edge.weight(), new HashSet<>());
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
        return new GraphPlan(tasks, List.copyOf(notices.values()), advice, pushes);
    }

    private GraphPlan.Task task(String key, int priority) {
        if (key.startsWith(EXPLORE)) {
            return new GraphPlan.Task(
                    key,
                    BoardCase.GATHER_MATERIALS,
                    key.substring(EXPLORE.length()),
                    Optional.empty(),
                    priority,
                    Map.of(),
                    true,
                    false,
                    false);
        }
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
                    stocked(event.consumes())
                            && (event.building() == null || state.built(event.building()) == Built.BUILT),
                    false,
                    true);
        }
        if (key.startsWith(BUILDING)) {
            String id = key.substring(BUILDING.length());
            SupplyGraph.Building building = Objects.requireNonNull(
                    graph.buildings().get(id), "Постройка " + id + " задания не описана в графе");
            Map<String, Integer> materials = materials(id);
            boolean tooled = (building.tool() == null || state.bestTier(building.tool()) >= 0)
                    && building.requires().stream().allMatch(required -> state.built(required) == Built.BUILT);
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
                tooled && housed(way) && available(way) && stocked(way.consumes()) && stocked(way.needs()),
                chances.contains(key),
                graph.implemented(wayId));
    }

    private Map<String, Integer> materials(String building) {
        SupplyGraph.Building known = graph.buildings().get(building);
        return state.materials().getOrDefault(building, known == null ? Map.of() : known.materials());
    }

    private int count(String node) {
        if (graph.isGroup(node)) {
            return graph.members(node).stream().mapToInt(state::count).sum();
        }
        return state.count(node);
    }

    private boolean available(SupplyGraph.Way way) {
        if (way.world() != null && !state.world().contains(way.world())) {
            return false;
        }
        for (String input : way.consumes().keySet()) {
            SupplyGraph.Item item = graph.items().get(input);
            if (item != null && item.stage() > state.stage()) {
                return false;
            }
        }
        SupplyGraph.Building building =
                way.building() == null ? null : graph.buildings().get(way.building());
        if (building == null) {
            return true;
        }
        boolean planned = !building.optional() || state.built(building.id()) != Built.ABSENT;
        return building.stage() <= state.stage() && planned;
    }

    private boolean sourced(String node) {
        if (graph.isGroup(node)) {
            return graph.members(node).stream().anyMatch(this::sourced);
        }
        List<SupplyGraph.Way> producers = graph.producers(node);
        return producers.isEmpty()
                || producers.stream()
                        .anyMatch(way -> available(way)
                                && way.consumes().keySet().stream()
                                        .allMatch(input -> count(input) > 0 || sourcedShallow(input)));
    }

    private boolean sourcedShallow(String node) {
        if (graph.isGroup(node)) {
            return graph.members(node).stream().anyMatch(this::sourcedShallow);
        }
        return graph.producers(node).stream().anyMatch(this::available);
    }

    private boolean housed(SupplyGraph.Way way) {
        return way.building() == null || state.built(way.building()) == Built.BUILT;
    }

    private boolean stocked(Map<String, Integer> needs) {
        return needs.entrySet().stream().allMatch(entry -> count(entry.getKey()) >= entry.getValue());
    }

    private static String toolKey(String kind, String spirit) {
        return TOOL + kind + AT + spirit;
    }
}
