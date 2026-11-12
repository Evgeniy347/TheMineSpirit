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
    void solve_CycleBuiltInOtherOrder_SameValues() {
        GraphFlow one = new GraphFlow();
        one.root("torch", 30);
        one.root("coal", 20);
        one.link("torch", "coal", 0.5);
        one.link("coal", "torch", 0.5);
        GraphFlow two = new GraphFlow();
        two.root("coal", 20);
        two.root("torch", 30);
        two.link("coal", "torch", 0.5);
        two.link("torch", "coal", 0.5);

        assertEquals(
                one.solve(),
                two.solve(),
                "Порядок вставки узлов и рёбер (обход Map.copyOf меняется от запуска JVM) не меняет поток: "
                        + "обратные рёбра выбираются в каноническом порядке ключей");
    }

    @Test
    void solve_Cycle_PressureDoesNotReturnToSource() {
        GraphFlow cyclic = new GraphFlow();
        cyclic.root("cobble", 10);
        cyclic.link("cobble", "pickaxe", 0.5);
        cyclic.link("pickaxe", "cobble", 3.0);
        GraphFlow straight = new GraphFlow();
        straight.root("cobble", 10);
        straight.link("cobble", "pickaxe", 0.5);

        Map<String, Double> values = cyclic.solve();

        assertEquals(
                straight.solve(),
                values,
                "Цикл булыжник ↔ кирка не разгоняет давление: обратное ребро в узел на текущем пути отбрасывается, "
                        + "даже с коэффициентом больше единицы");
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
    void priority_Scale_LogarithmicWithoutSaturation() {
        int ceiling = GraphPriorities.work(GraphWorld.GRAPH);

        assertTrue(
                GraphFlow.priority(0, ceiling) < GraphFlow.priority(1, ceiling)
                        && GraphFlow.priority(400, ceiling) < GraphFlow.priority(500, ceiling)
                        && GraphFlow.priority(500, ceiling) < GraphFlow.priority(900, ceiling)
                        && GraphFlow.priority(900, ceiling) < GraphFlow.priority(GraphFlow.SCORE_SPAN, ceiling)
                        && GraphFlow.priority(GraphFlow.SCORE_SPAN, ceiling) == ceiling,
                "Шкала работы логарифмическая до SCORE_SPAN: крупные давления различаются (раньше 400, 500 и "
                        + "900 сливались у потолка), ничто не поднимается выше потолка работы, ноль не поднят");
    }
}
