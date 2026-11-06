package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphOrdersTests {
    private static final String ORDER_EVENT = "player_craft_order";

    @Test
    void plan_OrderShortage_RaisesGatherToPlayerPriority() {
        GraphWorld world = GraphWorld.settled().event(ORDER_EVENT);
        GraphState plain = world.state();

        int without = bestPriority(GraphPlanner.plan(world.graph, plain), "coal");
        int with = bestPriority(GraphPlanner.plan(world.graph, plain.withOrders(Map.of("coal", 12))), "coal");

        assertTrue(
                with > without && with > 0,
                "Недостача заказа игрока идёт с приоритетом игрока (maxPriority): добыча угля обязана подняться, "
                        + "было " + without + ", стало " + with);
    }

    @Test
    void tickets_OrderShortage_CountCoversOrder() {
        GraphWorld world = GraphWorld.settled().event(ORDER_EVENT);
        GraphState state = world.state().withOrders(Map.of("coal", 12));

        GraphTickets.Result result = GraphTickets.of(world.graph, state, GraphPlanner.plan(world.graph, state));

        assertTrue(
                result.post().stream()
                        .anyMatch(ticket -> "coal".equals(ticket.task().makes()) && ticket.count() >= 12),
                "Задание доски на уголь обязано покрыть заказ (12), иначе заказ ждёт лишние круги: " + result.post());
    }

    @Test
    void plan_OrderItemUnknownToGraph_Notice() {
        GraphWorld world = GraphWorld.settled().event(ORDER_EVENT);

        GraphPlan plan = GraphPlanner.plan(world.graph, world.state().withOrders(Map.of("unobtainium", 1)));

        assertTrue(
                plan.notices().stream().anyMatch(notice -> notice.reason().contains("unobtainium")),
                "Предмет заказа, которого нет в графе, не теряется молча: стратег получает просьбу " + plan.notices());
    }

    private static int bestPriority(GraphPlan plan, String item) {
        return plan.tasks().stream()
                .filter(task -> item.equals(task.makes()))
                .mapToInt(GraphPlan.Task::priority)
                .max()
                .orElse(0);
    }
}
