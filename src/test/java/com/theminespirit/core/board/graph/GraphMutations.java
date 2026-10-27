package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

final class GraphMutations {
    record Mutation(String name, UnaryOperator<GraphWorld> apply) {}

    private GraphMutations() {}

    static List<GraphWorld> baselines() {
        GraphWorld empty = GraphWorld.empty();
        GraphWorld settled = GraphWorld.settled();
        GraphWorld crowded = GraphWorld.settled().tools("spirit-2", -1).tools("spirit-3", 2);
        GraphWorld second = GraphWorld.settled();
        second.stage = 2;
        GraphWorld.GRAPH.buildings().keySet().forEach(id -> second.built(id, Built.PLANNED));
        return List.of(empty, settled, crowded, second);
    }

    static List<Mutation> single() {
        List<Mutation> all = new ArrayList<>();
        for (SupplyGraph.Item item : GraphWorld.GRAPH.items().values()) {
            for (int count : List.of(0, Math.max(0, item.xmin() - 1), item.xmin() * 4, 10_000)) {
                all.add(new Mutation(
                        item.id() + "=" + count, world -> world.copy().with(item.id(), count)));
            }
        }
        for (int tier = -1; tier <= 2; tier++) {
            int chosen = tier;
            all.add(new Mutation("tools=" + tier, world -> world.copy().tools("spirit-1", chosen)));
        }
        for (String building : GraphWorld.GRAPH.buildings().keySet()) {
            for (Built built : Built.values()) {
                all.add(new Mutation(
                        building + "=" + built, world -> world.copy().built(building, built)));
            }
        }
        for (String event : GraphWorld.GRAPH.events().keySet()) {
            all.add(new Mutation("event=" + event, world -> world.copy().event(event)));
        }
        for (int free : List.of(0, 19, 20, 100)) {
            all.add(new Mutation("free=" + free, world -> {
                GraphWorld copy = world.copy();
                copy.storageFree = free;
                return copy;
            }));
        }
        return all;
    }
}
