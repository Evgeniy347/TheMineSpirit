package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class More7PlannerTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();
    private static final List<String> EVENTS = List.of(
            More7Rules.WORK_PILEUP,
            More7Rules.GATED_ITEM,
            More7Rules.MORNING_HARVEST,
            More7Rules.EVENING_RETURN,
            More7Rules.PEN_FULL,
            More7Rules.FIELD_FULL,
            More7Rules.ORDER_READY);

    @Test
    void eventStubs_Planned_AreNotPostedButNotLost() {
        for (String event : EVENTS) {
            GraphWorld world = GraphWorld.empty().event(event);
            GraphState state = world.state();
            GraphPlan plan = GraphPlanner.plan(GRAPH, state);
            GraphTickets.Result tickets = GraphTickets.of(GRAPH, state, plan);

            List<GraphPlan.Task> tasks = plan.tasks().stream()
                    .filter(task -> task.id().startsWith("event:" + event + ":"))
                    .toList();
            assertFalse(tasks.isEmpty(), "Событие области graphmore7 обязано дать задание: " + event);
            assertTrue(tasks.stream().noneMatch(GraphPlan.Task::implemented), "Заглушки не выдают себя за действия");
            assertTrue(
                    tasks.stream().allMatch(task -> task.priority() > 0), "Стартовый приоритет положителен: " + event);
            assertTrue(
                    tickets.post().stream()
                            .noneMatch(ticket -> tasks.stream()
                                    .anyMatch(task -> (GraphTickets.PREFIX + task.id()).equals(ticket.id()))),
                    "Несуществующее действие не ставится на доску: " + event);
            assertTrue(
                    tickets.unimplemented().stream()
                            .anyMatch(known ->
                                    tasks.stream().anyMatch(task -> task.id().equals(known.id()))),
                    "Заглушка попадает в список нереализованных: " + event);
        }
    }
}
