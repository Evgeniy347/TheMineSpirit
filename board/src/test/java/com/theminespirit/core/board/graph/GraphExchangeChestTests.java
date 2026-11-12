package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import org.junit.jupiter.api.Test;

class GraphExchangeChestTests {
    @Test
    void plan_TableBuiltNoChest_ExchangeChestBeforeYardChests() {
        GraphPlan plan = GraphWorld.empty()
                .built("crafting_table", Built.BUILT)
                .with("oak_planks", 16)
                .plan();

        GraphPlan.Task exchange = plan.task("building:exchange_chest").orElseThrow();
        assertTrue(
                exchange.feasible() && exchange.priority() > plan.priority("building:chests"),
                "Верстак стоит, сундуков нет — сундук у верстака для обмена раньше сундуков двора (те ждут "
                        + "расчистку): " + CrossScene.ranked(plan));
    }

    @Test
    void plan_NoTable_ExchangeChestWaits() {
        GraphPlan plan = GraphWorld.empty().with("oak_planks", 16).plan();

        assertFalse(
                plan.task("building:exchange_chest")
                        .map(GraphPlan.Task::feasible)
                        .orElse(false),
                "Без верстака сундук у верстака не ставится: " + CrossScene.ranked(plan));
    }
}
