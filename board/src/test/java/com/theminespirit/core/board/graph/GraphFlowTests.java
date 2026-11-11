package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphFlowTests {
    @Test
    void solve_Chain_DecaysAlongLinks() {
        GraphFlow flow = new GraphFlow();
        flow.root("torch", 40);
        flow.link("torch", "craft_torch", 1.0);
        flow.link("craft_torch", "coal", 0.5);

        Map<String, Double> values = flow.solve();

        assertEquals(20.0, values.get("coal"), 1e-9, "Цепочка факел → крафт → уголь: уголь получает 40 × 1 × 0.5");
    }

    @Test
    void solve_Cycle_ConvergesToGeometricSum() {
        GraphFlow flow = new GraphFlow();
        flow.root("cobble", 10);
        flow.link("cobble", "pickaxe", 0.5);
        flow.link("pickaxe", "cobble", 0.5);

        Map<String, Double> values = flow.solve();

        assertEquals(
                10 / (1 - 0.25),
                values.get("cobble"),
                1e-4,
                "Цикл булыжник ↔ кирка сходится к сумме геометрического ряда 10 / (1 − 0.25), а не разгоняется");
    }

    @Test
    void solve_Local_NotPropagated() {
        GraphFlow flow = new GraphFlow();
        flow.root("hut", 50);
        flow.local("hut", 60);
        flow.link("hut", "planks", 0.5);

        Map<String, Double> values = flow.solve();

        assertTrue(
                values.get("hut") == 110.0 && values.get("planks") == 25.0,
                "Надбавка готовой постройке — местная: поднимает хижину, но не её материалы: " + values);
    }

    @Test
    void priority_Scale_MonotonicAndBounded() {
        int ceiling = GraphWorld.GRAPH.maxPriority();
        assertTrue(
                GraphFlow.priority(0, ceiling) < GraphFlow.priority(50, ceiling)
                        && GraphFlow.priority(50, ceiling) < GraphFlow.priority(150, ceiling)
                        && GraphFlow.priority(10_000, ceiling) == ceiling,
                "Шкала 100·(1 − e^(−s/100)): ноль — 0, больше вклад — выше приоритет, не больше 100");
    }
}
