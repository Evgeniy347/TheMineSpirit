package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphReachabilityTests {
    private static final SupplyGraph GRAPH = GraphWorld.GRAPH;
    private static final int LAST_STAGE = GraphSimulation.LAST_STAGE;

    @Test
    void emptyWorldWithTrees_Simulated_ReachesLastStage() {
        GraphSimulation.Outcome outcome = GraphSimulation.run(GraphWorld.empty(), 12_000);

        assertTrue(
                outcome.finished(),
                "Пустой мир с деревьями обязан дойти до последнего этапа. Застрял: " + outcome.stuck());
    }

    @Test
    void mandatoryBuildings_Loaded_CloseToSourceOrSubstitute() {
        Set<String> items = new HashSet<>();
        Set<String> buildings = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (SupplyGraph.Building building : GRAPH.buildings().values()) {
                if (buildings.contains(building.id())) {
                    continue;
                }
                boolean ready = building.requires().stream().allMatch(buildings::contains)
                        && building.materials().keySet().stream().allMatch(item -> closed(item, items));
                if (ready) {
                    buildings.add(building.id());
                    changed = true;
                }
            }
            for (SupplyGraph.Way way : GRAPH.ways()) {
                if (items.contains(way.makes())) {
                    continue;
                }
                boolean ready = way.world() != null
                        || ((way.building() == null || buildings.contains(way.building()))
                                && way.consumes().keySet().stream().allMatch(item -> closed(item, items))
                                && way.needs().keySet().stream().allMatch(item -> closed(item, items)));
                if (ready) {
                    items.add(way.makes());
                    changed = true;
                }
            }
        }

        List<String> holes = new ArrayList<>();
        for (SupplyGraph.Building building : GRAPH.buildings().values()) {
            if (building.optional() || building.stage() > LAST_STAGE || buildings.contains(building.id())) {
                continue;
            }
            String cycle = cycle(building.id(), new LinkedHashSet<>());
            holes.add(building.id()
                    + (cycle == null
                            ? ": нет источника у материалов " + building.materials()
                            : ": цикл зависимостей " + cycle));
        }
        assertEquals(List.of(), holes, "Обязательная постройка этапа недостижима из пустого мира");
    }

    private static boolean closed(String node, Set<String> items) {
        if (GRAPH.isGroup(node)) {
            return GRAPH.members(node).stream().anyMatch(member -> closed(member, items));
        }
        return items.contains(node);
    }

    private static String cycle(String node, Set<String> path) {
        if (!path.add(node)) {
            return path.toString() + " → " + node;
        }
        List<String> next = new ArrayList<>();
        SupplyGraph.Building building = GRAPH.buildings().get(node);
        if (building != null) {
            next.addAll(building.materials().keySet());
            next.addAll(building.requires());
        } else {
            for (SupplyGraph.Way way : GRAPH.producers(node)) {
                next.addAll(way.consumes().keySet());
                next.addAll(way.needs().keySet());
                if (way.building() != null) {
                    next.add(way.building());
                }
            }
            if (GRAPH.isGroup(node)) {
                next.addAll(GRAPH.members(node));
            }
        }
        for (String child : next) {
            String found = cycle(child, path);
            if (found != null) {
                return found;
            }
        }
        path.remove(node);
        return null;
    }
}
