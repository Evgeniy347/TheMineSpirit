package com.theminespirit.core.board.graph;

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
}
