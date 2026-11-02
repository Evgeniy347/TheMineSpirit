package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class More2PlannerTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();
    private static final String FIRE = "event:" + More2Rules.FIRE_NEAR_BUILDING + ":more2_put_out_fire";

    @Test
    void eventStub_PlannedStub_IsNotPosted() {
        GraphWorld world = GraphWorld.empty().event(More2Rules.FIRE_NEAR_BUILDING);
        GraphState state = world.state();
        GraphPlan plan = GraphPlanner.plan(GRAPH, state);

        GraphPlan.Task task = plan.tasks().stream()
                .filter(candidate -> candidate.id().equals(FIRE))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Событие огня обязано дать задание в плане: " + FIRE));
        assertFalse(task.implemented(), "Заглушка «тушить огонь» ещё не реализована");
        assertTrue(task.priority() > 0, "Пожар у постройки — заметный приоритет, а не ноль");

        GraphTickets.Result tickets = GraphTickets.of(GRAPH, state, plan);
        assertTrue(
                tickets.post().stream().noneMatch(ticket -> ticket.id().equals(GraphTickets.PREFIX + FIRE)),
                "Реализация-заглушка не ставится на доску: дух не должен брать несуществующее действие");
        assertTrue(
                tickets.unimplemented().stream().anyMatch(known -> known.id().equals(FIRE)),
                "Заглушка попадает в список нереализованных, а не теряется молча");
    }

    @Test
    void implementedEvent_DefaultFlag_StaysPosted() {
        GraphWorld world = GraphWorld.empty().event("crop_ready");
        GraphState state = world.state();
        GraphPlan plan = GraphPlanner.plan(GRAPH, state);
        String harvest = "event:crop_ready:harvest_crop";

        GraphTickets.Result tickets = GraphTickets.of(GRAPH, state, plan);

        assertTrue(
                tickets.unimplemented().stream().noneMatch(known -> known.id().equals(harvest)),
                "Готовое событие сбора не попадает в нереализованные");
        assertTrue(
                plan.tasks().stream().anyMatch(task -> task.id().equals(harvest) && task.implemented()),
                "Флаг implemented по умолчанию остаётся true для старых событий");
    }
}
