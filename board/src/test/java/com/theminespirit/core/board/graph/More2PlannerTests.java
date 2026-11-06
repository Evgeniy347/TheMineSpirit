package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class More2PlannerTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();
    private static final String FIRE = "event:" + More2Rules.FIRE_NEAR_BUILDING + ":more2_put_out_fire";

    @Test
    void implementedEvent_FlagTrue_IsPosted() {
        GraphWorld world = GraphWorld.empty().event(More2Rules.FIRE_NEAR_BUILDING);
        GraphState state = world.state();
        GraphPlan plan = GraphPlanner.plan(GRAPH, state);

        GraphPlan.Task task = plan.tasks().stream()
                .filter(candidate -> FIRE.equals(candidate.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Событие огня обязано дать задание в плане: " + FIRE));
        assertTrue(task.implemented(), "Тушение огня связано с исполнением");
        assertTrue(task.priority() > 0, "Пожар у постройки — заметный приоритет, а не ноль");

        GraphTickets.Result tickets = GraphTickets.of(GRAPH, state, plan);
        assertTrue(
                tickets.post().stream().anyMatch(ticket -> (GraphTickets.PREFIX + FIRE).equals(ticket.id())),
                "Реализованное действие ставится на доску: дух тушит огонь");
        assertTrue(
                tickets.unimplemented().stream().noneMatch(known -> FIRE.equals(known.id())),
                "Реализованное действие не попадает в список нереализованных");
    }

    @Test
    void removedVillagerTrade_GraphLoaded_IsAbsent() {
        assertTrue(
                GRAPH.way("more2_trade_villager").isEmpty(),
                "Торговля с жителем удалена: у духов нет такого действия и потребности в нём");
        assertTrue(
                GRAPH.events().get(More2Rules.VILLAGER_NEAR).tasks().stream()
                        .noneMatch(task -> "more2_trade_villager".equals(task.id())),
                "Событие жителей рядом ставит только охрану");
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
