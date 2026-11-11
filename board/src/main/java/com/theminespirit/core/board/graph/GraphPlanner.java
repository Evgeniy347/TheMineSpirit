package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class GraphPlanner {
    private static final String BUILDING = "building:";
    private static final String TOOL = "tool:";
    private static final String EVENT = "event:";
    private static final String EXPLORE = "explore:";
    private static final String STATION = "station:";
    private static final String ORDER = "order:";
    private static final int EXPLORE_TICKS = 2400;
    private static final char AT = '@';
    final Map<String, GraphPlan.Notice> notices = new LinkedHashMap<>();
    private final SupplyGraph graph;
    private final GraphState state;
    private final GraphReach reach;
    private final GraphFlow flow = new GraphFlow();
    private final GraphChoiceRules choices;
    private final GraphBlocks blockRules;
    private final Set<String> expanded = new HashSet<>();
    private final Set<String> chances = new HashSet<>();
    private final List<GraphPlan.Advice> advice = new ArrayList<>();

    private GraphPlanner(SupplyGraph graph, GraphState state) {
        this.graph = Objects.requireNonNull(graph, "Граф не может быть null: без него не из чего выводить задания");
        this.state = Objects.requireNonNull(state, "Состояние не может быть null: задания выводятся из нехваток");
        this.reach = new GraphReach(graph, state);
        this.choices = new GraphChoiceRules(graph, state, reach);
        this.blockRules = new GraphBlocks(graph, state, flow, this::push, notices);
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
                push(null, item.id(), item.base() + (have < item.xmin() ? graph.urgentBoost() : 0));
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
                    push(null, TOOL + kind + AT + spirit, base + (tier < 0 ? graph.urgentBoost() : 0));
                }
            }
        }
        for (SupplyGraph.Building building : graph.buildings().values()) {
            if (building.stage() <= state.stage()
                    && state.built(building.id()) != Built.BUILT
                    && (!building.optional() || state.built(building.id()) == Built.PLANNED)) {
                requireBuilding(null, building.id(), building.base(), "постройка этапа " + building.stage());
            }
        }
        graph.herdCaps().forEach((herd, herdCap) -> {
            if (count(herd) > herdCap.limit(state.spirits())) {
                for (SupplyGraph.Way way : graph.ways()) {
                    if (way.consumes().containsKey(herd) && reach.available(way)) {
                        flow.local(way.id(), graph.herdCullBoost());
                        expandWay(way.id(), way);
                    }
                }
            }
        });
        for (SupplyGraph.Trigger trigger : graph.triggers()) {
            SupplyGraph.Building wanted = graph.buildings().get(trigger.build());
            if (wanted != null && count(trigger.have()) > 0 && state.built(trigger.build()) != Built.BUILT) {
                requireBuilding(null, trigger.build(), wanted.base(), "есть " + trigger.have());
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
                    null,
                    graph.storage().building(),
                    graph.storage().base(),
                    "на складе свободно " + state.storageFreePercent() + "% (< "
                            + graph.storage().minFreePercent() + "%)");
        }
        GraphStore.roots(graph, state, this::push);
        DigOperations.roots(graph, state, this::push);
    }

    private void push(@Nullable String from, String node, double amount) {
        if (amount == 0) return;
        if (from == null) {
            flow.root(node, amount);
        } else {
            flow.link(from, node, amount);
        }
        expand(node);
    }

    private void expand(String node) {
        if (!expanded.add(node)) return;
        double decay = graph.decay();
        if (node.startsWith(TOOL)) {
            String kind = node.substring(TOOL.length(), node.indexOf(AT));
            String spirit = node.substring(node.indexOf(AT) + 1);
            if (ChestTools.covered(graph, state).contains(kind + AT + spirit)) return;
            for (String tier : graph.tiers()) {
                if (graph.rank(tier) <= state.tier(spirit, kind)) {
                    continue;
                }
                boolean aboveKit = graph.rank(tier) > graph.rank(graph.kit().tier(kind, state.stage()));
                for (SupplyGraph.Way way : reach.producers(tier + "_" + kind)) {
                    String key = way.id() + AT + spirit;
                    flow.link(node, key, way.weight());
                    if (aboveKit) {
                        chances.add(key);
                    } else {
                        expandWay(key, way);
                    }
                }
            }
        } else if (node.startsWith(BUILDING)) {
            String id = node.substring(BUILDING.length());
            SupplyGraph.Building building = graph.buildings().get(id);
            Map<String, Integer> needs = GraphLack.materials(graph, state, id);
            if (building != null
                    && GraphLack.stocked(graph, state, needs)
                    && (building.tool() == null || state.bestTier(building.tool()) >= 0)) {
                flow.local(node, graph.buildReadyBoost());
            }
            for (Map.Entry<String, Integer> input : needs.entrySet()) {
                if (count(input.getKey()) < input.getValue()) {
                    push(node, input.getKey(), decay);
                }
            }
            if (building != null) {
                for (String required : building.requires()) {
                    if (state.built(required) != Built.BUILT) {
                        requireBuilding(node, required, decay, "нужна постройке " + id);
                    }
                }
                if (building.tool() != null && state.bestTier(building.tool()) < 0) {
                    pushTool(node, building.tool(), decay);
                }
            }
        } else if (node.startsWith(EVENT) || node.startsWith(EXPLORE)) {
            return;
        } else if (graph.isGroup(node)) {
            for (String member : graph.members(node)) {
                if (reach.sourced(member)) {
                    push(node, member, 1.0);
                }
            }
        } else {
            List<SupplyGraph.Way> producers = reach.producers(node);
            for (SupplyGraph.Way way : producers) {
                if (reach.available(way)) {
                    flow.link(node, way.id(), way.weight());
                    expandWay(way.id(), way);
                }
            }
            List<String> missing = producers.stream()
                    .map(SupplyGraph.Way::world)
                    .filter(id -> id != null && !state.world().contains(id) && !id.startsWith("more:"))
                    .distinct()
                    .toList();
            if (!missing.isEmpty() && !reach.sourced(node)) {
                notices.putIfAbsent(
                        node,
                        new GraphPlan.Notice(
                                node,
                                "нет источника " + node + " в округе (нет " + missing
                                        + "): разведать или сменить материал/породу постройки"));
                missing.forEach(resource -> flow.link(node, EXPLORE + resource, decay));
            }
        }
    }

    private void expandWay(String key, SupplyGraph.Way way) {
        if (!expanded.add(key)) {
            return;
        }
        double decay = graph.decay();
        for (Map.Entry<String, Integer> input : way.consumes().entrySet()) {
            if (count(input.getKey()) < input.getValue()) {
                push(key, input.getKey(), decay);
            }
        }
        for (Map.Entry<String, Integer> input : way.needs().entrySet()) {
            if (count(input.getKey()) < input.getValue()) {
                push(key, input.getKey(), decay);
            }
        }
        if (way.tool() != null && state.bestTier(way.tool()) < graph.rank(way.tier() == null ? "wooden" : way.tier())) {
            pushTool(key, way.tool(), decay);
        }
        if (way.building() != null && state.built(way.building()) != Built.BUILT) {
            requireBuilding(key, way.building(), decay, "нужна пути " + way.id());
        }
    }

    private void pushTool(@Nullable String from, String kind, double amount) {
        for (String spirit : state.kits().keySet()) {
            push(from, TOOL + kind + AT + spirit, amount / Math.max(1, state.spirits()));
        }
    }

    private void requireBuilding(@Nullable String from, String id, double amount, String why) {
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
        if (StageCatalog.watcherOwned(building.boardCase()) || GraphAudit.settles(state, id)) return;
        push(from, BUILDING + id, amount);
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
                        push(key, input.getKey(), graph.decay());
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
                push(null, item, graph.maxPriority());
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
                    push(null, TOOL + kind + AT + spirit, weight);
                }
            }
        } else if (node.startsWith(BUILDING)) {
            String id = node.substring(BUILDING.length());
            if (state.built(id) != Built.BUILT) {
                requireBuilding(null, id, weight, "событие " + event);
            }
        } else if (GraphLack.lacking(graph, state, node)) {
            push(null, node, weight);
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
                    flow.local(key, edge.weight());
                    active = true;
                }
            }
            SupplyGraph.Way way = reach.way(edge.way()).orElse(null);
            if (!active && edge.weight() > 0 && way != null && !graph.isTool(way.makes()) && reach.available(way)) {
                flow.root(way.id(), edge.weight());
                expandWay(way.id(), way);
                changed = true;
            } else if (active) {
                changed = true;
            }
        }
        return changed ? flow.solve() : scores;
    }

    private boolean task(String key) {
        if (key.startsWith(BUILDING) || key.startsWith(EVENT) || key.startsWith(EXPLORE)) {
            return true;
        }
        int at = key.indexOf(AT);
        return !key.startsWith(TOOL)
                && reach.way(at < 0 ? key : key.substring(0, at)).isPresent();
    }

    private GraphPlan result(Map<String, Double> scores) {
        List<GraphPlan.Task> tasks = new ArrayList<>();
        Map<String, Double> load = new LinkedHashMap<>();
        Set<String> made = new HashSet<>();
        for (Map.Entry<String, Double> score : GraphChoiceRules.ordered(scores)) {
            if (score.getValue() <= 0 || !task(score.getKey())) {
                continue;
            }
            boolean repeat = !made.add(choices.makes(score.getKey()));
            double value = score.getValue() * graph.costs().travelFactor(choices.travelTicks(score.getKey()));
            double adjusted =
                    choices.value(score.getKey(), value, repeat) * BuildingShare.of(graph, state, score.getKey());
            GraphPlan.Task task = task(score.getKey(), GraphEvent.priority(state, graph, score.getKey(), adjusted));
            String reason = blockRules.reason(score.getKey());
            if (reason != null) {
                task = GraphBlocks.blockedTask(task, reason);
            }
            GraphPlan.Task ready = task;
            tasks.add(ready);
            station(score.getKey())
                    .filter(ignored -> ready.feasible())
                    .ifPresent(building -> load.merge(building, adjusted, Math::max));
        }
        load.forEach((building, value) -> stationTask(building, value, scores).ifPresent(tasks::add));
        return new GraphPlan(tasks, List.copyOf(notices.values()), advice);
    }

    private Optional<String> station(String key) {
        int at = key.indexOf(AT);
        return reach.way(at < 0 ? key : key.substring(0, at))
                .map(SupplyGraph.Way::building)
                .filter(building -> graph.costs().stationCapacity().containsKey(building));
    }

    private Optional<GraphPlan.Task> stationTask(String building, double value, Map<String, Double> scores) {
        long busy = scores.entrySet().stream()
                .filter(entry -> entry.getValue() > 0 && task(entry.getKey()))
                .filter(entry ->
                        station(entry.getKey()).filter(building::equals).isPresent())
                .filter(entry -> task(entry.getKey(), 0).feasible())
                .count();
        int capacity = graph.costs().stationCapacity().getOrDefault(building, Integer.MAX_VALUE);
        if (state.built(building) != Built.BUILT || busy <= (long) state.stations(building) * capacity) {
            return Optional.empty();
        }
        SupplyGraph.Building known = Objects.requireNonNull(
                graph.buildings().get(building), "Станция " + building + " не описана среди построек графа");
        Map<String, Integer> materials = GraphLack.materials(graph, state, building);
        return Optional.of(new GraphPlan.Task(
                STATION + building,
                known.boardCase(),
                building,
                Optional.empty(),
                GraphFlow.priority(value * graph.decay(), GraphPriorities.work(graph)),
                materials,
                GraphLack.stocked(graph, state, materials),
                false,
                graph.implemented(building),
                graph.costs().ticks(STATION + building, known.boardCase())));
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
                    false,
                    EXPLORE_TICKS);
        }
        if (key.startsWith(EVENT)) {
            String[] parts = key.split(":", 3);
            GraphEvent event = Objects.requireNonNull(
                    graph.events().get(parts[1]), "Событие " + parts[1] + " задания не описано в графе");
            GraphEvent.Task chosen = Objects.requireNonNull(
                    GraphEvent.find(event, parts[2]),
                    "Событие " + parts[1] + " не описывает задание " + parts[2] + ": узел графа обязан помнить кейс");
            return new GraphPlan.Task(
                    key,
                    chosen.boardCase(),
                    parts[2],
                    GraphEventFacts.owner(state, chosen),
                    priority,
                    GraphEventFacts.consumes(state, chosen),
                    GraphEventFacts.feasible(graph, state, chosen),
                    false,
                    chosen.implemented(),
                    graph.costs().ticks(key, chosen.boardCase()));
        }
        if (key.startsWith(BUILDING)) {
            String id = key.substring(BUILDING.length());
            SupplyGraph.Building building = Objects.requireNonNull(
                    graph.buildings().get(id), "Постройка " + id + " задания не описана в графе");
            Map<String, Integer> materials = GraphLack.materials(graph, state, id);
            boolean tooled = (building.tool() == null || state.bestTier(building.tool()) >= 0)
                    && building.requires().stream().allMatch(required -> state.built(required) == Built.BUILT);
            return new GraphPlan.Task(
                    key,
                    building.boardCase(),
                    id,
                    Optional.empty(),
                    priority,
                    materials,
                    tooled && GraphLack.share(graph, state, building.materials()) >= graph.buildStartShare(),
                    false,
                    graph.implemented(id),
                    graph.costs().ticks(key, building.boardCase()));
        }
        int at = key.indexOf(AT);
        String wayId = at < 0 ? key : key.substring(0, at);
        SupplyGraph.Way way = reach.way(wayId).orElseThrow();
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
                tooled
                        && GraphLack.housed(state, way)
                        && reach.available(way)
                        && GraphLack.stocked(graph, state, way.consumes())
                        && GraphLack.stocked(graph, state, way.needs()),
                chances.contains(key),
                graph.implemented(wayId),
                choices.wayTicks(key, way));
    }

    private int count(String node) {
        return GraphLack.count(graph, state, node);
    }
}
