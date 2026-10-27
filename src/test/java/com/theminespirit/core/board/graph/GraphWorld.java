package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GraphWorld {
    static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    final Map<String, Integer> stock = new HashMap<>();
    final Map<String, Map<String, Integer>> kits = new LinkedHashMap<>();
    final Map<String, Built> buildings = new HashMap<>();
    int stage = 1;
    int storageFree = 60;

    static GraphWorld empty() {
        GraphWorld world = new GraphWorld();
        world.kits.put("spirit-1", new HashMap<>());
        GRAPH.buildings()
                .values()
                .forEach(building ->
                        world.buildings.put(building.id(), building.stage() == 1 ? Built.PLANNED : Built.ABSENT));
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
        for (String kind : GRAPH.kit().kinds()) {
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

    GraphWorld built(String building, Built state) {
        buildings.put(building, state);
        return this;
    }

    GraphWorld copy() {
        GraphWorld world = new GraphWorld();
        world.stock.putAll(stock);
        kits.forEach((spirit, kit) -> world.kits.put(spirit, new HashMap<>(kit)));
        world.buildings.putAll(buildings);
        world.stage = stage;
        world.storageFree = storageFree;
        return world;
    }

    GraphState state() {
        return new GraphState(stock, stage, kits, buildings, Map.of(), storageFree);
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
        return GraphPlanner.plan(GRAPH, state());
    }
}
