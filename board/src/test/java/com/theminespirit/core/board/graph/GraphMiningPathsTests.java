package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphMiningPathsTests {
    @Test
    void plan_CopperOrder_MineCopperImplemented() {
        GraphWorld world = GraphWorld.settled().built("mine_level_2", Built.BUILT);
        world.stage = 2;
        GraphState state = world.state().withOrders(Map.of("raw_copper", 8));
        GraphPlan plan = GraphPlanner.plan(GraphWorld.GRAPH, state);

        assertTrue(
                plan.task("mine_copper")
                        .map(task -> task.implemented() && task.feasible())
                        .orElse(false),
                "Заказ меди — путь mine_copper реализован и выполним со второго уровня шахты каменной киркой: "
                        + plan.task("mine_copper"));
    }

    @Test
    void plan_EmeraldOrder_MineEmeraldImplemented() {
        GraphWorld world = GraphWorld.settled().tools("spirit-1", 3);
        world.stage = 3;
        world.world.add("emerald_ore");
        GraphState state = world.state().withOrders(Map.of("emerald", 2));
        GraphPlan plan = GraphPlanner.plan(GraphWorld.GRAPH, state);

        assertTrue(
                plan.task("mine_emerald")
                        .map(task -> task.implemented() && task.feasible())
                        .orElse(false),
                "Заказ изумруда — путь mine_emerald реализован и выполним у изумрудной руды алмазной киркой: "
                        + plan.task("mine_emerald"));
    }

    @Test
    void plan_EmeraldOrderNoOre_NotFeasible() {
        GraphWorld world = GraphWorld.settled().tools("spirit-1", 3);
        world.stage = 3;
        GraphState state = world.state().withOrders(Map.of("emerald", 2));
        GraphPlan plan = GraphPlanner.plan(GraphWorld.GRAPH, state);

        assertFalse(
                plan.task("mine_emerald").map(GraphPlan.Task::feasible).orElse(false),
                "Изумрудной руды в округе нет — добыча ждёт разведки, а не идёт вслепую: " + plan.task("mine_emerald"));
    }
}
