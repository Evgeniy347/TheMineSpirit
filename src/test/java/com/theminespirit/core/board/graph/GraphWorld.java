package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

final class GraphWorld {
    static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    final Map<String, Integer> stock = new HashMap<>();
    final Map<String, Map<String, Integer>> kits = new LinkedHashMap<>();
    final Map<String, Built> buildings = new HashMap<>();
    int stage = 1;
    int storageFree = 60;
    final Set<String> events = new HashSet<>();
    SupplyGraph graph = GRAPH;
    final Map<String, Map<String, Integer>> materials = new HashMap<>();
    final Map<String, Integer> stations = new HashMap<>();
    final Map<String, Integer> distances = new HashMap<>();
    final Set<String> world = new HashSet<>(
            Set.of("tree:oak", "tree:jungle", "sand", "clay", "animals", "flower:poppy", "flower:dandelion", "soil"));
    GraphFacts facts = GraphFacts.empty();

    static GraphWorld empty() {
        GraphWorld world = new GraphWorld();
        world.kits.put("spirit-1", new HashMap<>());
        GRAPH.buildings()
                .values()
                .forEach(building -> world.buildings.put(
                        building.id(), building.stage() == 1 && !building.optional() ? Built.PLANNED : Built.ABSENT));
        return world;
    }

    static GraphWorld settled() {
        GraphWorld world = empty();
        world.buildings.put("crafting_table", Built.BUILT);
        world.buildings.put("furnace", Built.BUILT);
        world.buildings.put("clearing", Built.BUILT);
        world.buildings.put("chests", Built.BUILT);
        world.buildings.put("mine", Built.BUILT);
        world.tools("spirit-1", 1);
        world.stock.putAll(Map.of("oak_log", 30, "oak_planks", 20, "stick", 10, "cobblestone", 40, "coal", 4));
        return world;
    }

    GraphWorld tools(String spirit, int tier) {
        Map<String, Integer> kit = new HashMap<>();
        for (String kind : graph.kit().kinds()) {
            if (tier >= 0) {
                kit.put(kind, tier);
            }
        }
        kits.put(spirit, kit);
        return this;
    }

    GraphWorld with(String item, int count) {
        stock.put(item, count);
        return this;
    }

    GraphWorld event(String name) {
        events.add(name);
        return this;
    }

    GraphWorld facts(GraphFacts value) {
        facts = value;
        return this;
    }

    GraphWorld lotReady(String building, boolean ready) {
        return building(
                building,
                before -> new GraphFacts.Building(
                        before.id(),
                        before.level(),
                        before.auditPercent(),
                        before.damage(),
                        before.missing(),
                        before.extra(),
                        before.wrong(),
                        before.position(),
                        before.unknown(),
                        ready));
    }

    GraphWorld wrong(String building, int wrong) {
        return building(
                building,
                before -> new GraphFacts.Building(
                        before.id(),
                        before.level(),
                        before.auditPercent(),
                        before.damage(),
                        before.missing(),
                        before.extra(),
                        wrong,
                        before.position(),
                        before.unknown(),
                        before.lotReady()));
    }

    private GraphWorld building(String id, UnaryOperator<GraphFacts.Building> change) {
        GraphFacts.Building before = facts.buildings()
                .getOrDefault(id, new GraphFacts.Building(id, 0, 0, 0, Map.of(), Map.of(), 0, null, false, true));
        Map<String, GraphFacts.Building> next = new LinkedHashMap<>(facts.buildings());
        next.put(id, change.apply(before));
        facts = new GraphFacts(
                facts.spirits(),
                facts.threats(),
                facts.chests(),
                facts.resources(),
                facts.stations(),
                next,
                facts.strays(),
                facts.terrain(),
                facts.stageReqs(),
                facts.feedback());
        return this;
    }

    GraphWorld built(String building, Built state) {
        buildings.put(building, state);
        return this;
    }

    GraphWorld needs(String building, String item, int count) {
        materials.put(building, Map.of(item, count));
        return this;
    }

    GraphWorld world(String tag) {
        world.add(tag);
        return this;
    }

    GraphWorld noWorld(String tag) {
        world.remove(tag);
        return this;
    }

    GraphWorld distance(String tag, int value) {
        distances.put(tag, value);
        return this;
    }

    GraphWorld copy() {
        GraphWorld world = new GraphWorld();
        world.stock.putAll(stock);
        kits.forEach((spirit, kit) -> world.kits.put(spirit, new HashMap<>(kit)));
        world.buildings.putAll(buildings);
        world.stage = stage;
        world.storageFree = storageFree;
        world.events.addAll(events);
        world.graph = graph;
        world.materials.putAll(materials);
        world.stations.putAll(stations);
        world.distances.putAll(distances);
        world.world.clear();
        world.world.addAll(this.world);
        world.facts = facts;
        return world;
    }

    GraphState state() {
        return new GraphState(
                        stock,
                        stage,
                        kits,
                        buildings,
                        materials,
                        storageFree,
                        events,
                        world,
                        distances,
                        stations,
                        Map.of())
                .withFacts(facts);
    }

    static List<GraphPlan.Task> issueOrder(GraphPlan plan) {
        return plan.tasks().stream()
                .filter(GraphPlan.Task::feasible)
                .sorted(Comparator.comparingInt(GraphPlan.Task::priority)
                        .reversed()
                        .thenComparing(GraphPlan.Task::id))
                .toList();
    }

    GraphPlan plan() {
        return GraphPlanner.plan(graph, state());
    }
}
