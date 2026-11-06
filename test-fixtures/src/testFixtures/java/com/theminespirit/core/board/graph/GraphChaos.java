package com.theminespirit.core.board.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

final class GraphChaos {
    static final int MAX_SPIRITS = 5;

    private GraphChaos() {}

    static GraphSimulation.Disturbance chaos(Random random, double arrive, double leave, double wear) {
        return (world, last) -> {
            for (String event : world.graph.events().keySet()) {
                if (world.events.contains(event)) {
                    if (random.nextDouble() < leave) {
                        world.events.remove(event);
                    }
                } else if (random.nextDouble() < arrive / world.graph.events().size()) {
                    world.events.add(event);
                }
            }
            world.graph.way(last.id().split("@", 2)[0]).ifPresent(way -> {
                if (way.tool() != null && random.nextDouble() < wear) {
                    breakTool(world, way.tool());
                }
            });
            if (last.id().startsWith("building:")
                    && last.makes().contains("house")
                    && world.kits.size() < MAX_SPIRITS) {
                world.kits.put("spirit-" + (world.kits.size() + 1), new HashMap<>());
            }
        };
    }

    private static void breakTool(GraphWorld world, String kind) {
        world.kits.values().stream()
                .filter(kit -> kit.getOrDefault(kind, -1) >= 0)
                .findFirst()
                .ifPresent(kit -> kit.remove(kind));
    }

    static SupplyGraph perturbed(SupplyGraph graph, Random random, double spread) {
        Map<String, SupplyGraph.Item> items = new LinkedHashMap<>();
        graph.items()
                .forEach((id, item) -> items.put(
                        id,
                        new SupplyGraph.Item(
                                id,
                                item.xmin(),
                                item.k(),
                                scale(item.base(), random, spread),
                                item.stage(),
                                item.perSpirit())));
        Map<String, SupplyGraph.Building> buildings = new LinkedHashMap<>();
        graph.buildings()
                .forEach((id, building) -> buildings.put(
                        id,
                        new SupplyGraph.Building(
                                id,
                                building.boardCase(),
                                building.stage(),
                                scale(building.base(), random, spread),
                                building.tool(),
                                building.materials(),
                                building.requires(),
                                building.advice(),
                                building.optional())));
        List<SupplyGraph.Way> ways = new ArrayList<>();
        for (SupplyGraph.Way way : graph.ways()) {
            ways.add(new SupplyGraph.Way(
                    way.id(),
                    way.boardCase(),
                    way.makes(),
                    way.yield(),
                    way.weight() * factor(random, spread),
                    way.consumes(),
                    way.tool(),
                    way.tier(),
                    way.building(),
                    way.world(),
                    way.needs()));
        }
        return new SupplyGraph(
                Math.min(0.95, graph.decay() * factor(random, spread)),
                scale(graph.urgentBoost(), random, spread),
                scale(graph.buildReadyBoost(), random, spread),
                graph.maxPriority(),
                graph.tiers(),
                graph.kit(),
                graph.storage(),
                items,
                buildings,
                ways,
                graph.edges(),
                graph.unimplemented(),
                graph.events(),
                graph.groups(),
                graph.triggers(),
                graph.herdCaps(),
                graph.buildStartShare(),
                graph.costs());
    }

    static List<GraphMutations.Mutation> combo(Random random, int size) {
        return combo(random, size, GraphMutations.single());
    }

    static List<GraphMutations.Mutation> combo(Random random, int size, List<GraphMutations.Mutation> source) {
        List<GraphMutations.Mutation> all = new ArrayList<>(source);
        List<GraphMutations.Mutation> chosen = new ArrayList<>();
        for (int index = 0; index < size && !all.isEmpty(); index++) {
            chosen.add(all.remove(random.nextInt(all.size())));
        }
        return chosen;
    }

    static GraphWorld apply(GraphWorld base, List<GraphMutations.Mutation> mutations) {
        GraphWorld world = base;
        for (GraphMutations.Mutation mutation : mutations) {
            world = mutation.apply().apply(world);
        }
        return world;
    }

    static String names(List<GraphMutations.Mutation> mutations) {
        return mutations.stream().map(GraphMutations.Mutation::name).toList().toString();
    }

    private static double factor(Random random, double spread) {
        return 1.0 + (random.nextDouble() * 2 - 1) * spread;
    }

    private static int scale(int value, Random random, double spread) {
        return (int) Math.round(value * factor(random, spread));
    }
}
