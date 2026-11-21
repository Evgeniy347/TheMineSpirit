package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class More4PlannerTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();
    private static final String NEAR = "event:" + More4Rules.PLAYER_NEAR + ":more4_guard_settlement";

    @Test
    void eventImplemented_PlannedTask_IsPosted() {
        GraphWorld world = GraphWorld.empty().event(More4Rules.PLAYER_NEAR);
        GraphState state = world.state();
        GraphPlan plan = GraphPlanner.plan(GRAPH, state);

        GraphPlan.Task task = plan.tasks().stream()
                .filter(candidate -> NEAR.equals(candidate.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Чужой игрок обязан дать задание охраны: " + NEAR));
        assertTrue(
                task.implemented(),
                "Охрана поселения подключена к действию"
                        + " Иначе тест eventImplemented_PlannedTask_IsPosted перестанет ловить эту поломку. При смене eventImplemented_PlannedTask_IsPosted проверь это утверждение.");
        assertTrue(
                task.raised(),
                "Игрок у поселения — заметный приоритет, а не ноль"
                        + " Иначе тест eventImplemented_PlannedTask_IsPosted перестанет ловить эту поломку. При смене eventImplemented_PlannedTask_IsPosted проверь это утверждение.");

        GraphTickets.Result tickets = GraphTickets.of(GRAPH, state, plan);
        assertTrue(
                tickets.post().stream().anyMatch(ticket -> (GraphTickets.PREFIX + NEAR).equals(ticket.id())),
                "Реализованное действие ставится на доску: дух обязан взять охрану"
                        + " Иначе тест eventImplemented_PlannedTask_IsPosted перестанет ловить эту поломку. При смене eventImplemented_PlannedTask_IsPosted проверь это утверждение.");
    }

    @Test
    void eventSpaceFound_TunesBuildingChoice_NotSeparateWork() {
        GraphState state = GraphWorld.empty().event(More4Rules.SPACE_FOUND).state();
        GraphPlan plan = GraphPlanner.plan(GRAPH, state);

        assertTrue(
                plan.tasks().stream().noneMatch(task -> task.id().startsWith("event:" + More4Rules.SPACE_FOUND + ":")),
                "Свободное место — условие выбора постройки, а не работа духа"
                        + " Иначе тест eventSpaceFound_TunesBuildingChoice_NotSeparateWork перестанет ловить эту поломку. При смене eventSpaceFound_TunesBuildingChoice_NotSeparateWork проверь это утверждение.");
        assertTrue(
                GRAPH.way("more4_plan_expansion").isEmpty()
                        && GRAPH.way("more4_find_space").isEmpty(),
                "Пути-заглушки расширения удалены: поправка живёт в graph.json.choice"
                        + " Иначе тест eventSpaceFound_TunesBuildingChoice_NotSeparateWork перестанет ловить эту поломку. При смене eventSpaceFound_TunesBuildingChoice_NotSeparateWork проверь это утверждение.");
    }

    @Test
    void eventTask_EveryAreaEvent_GivesPlannedWork() {
        for (String event : List.of(
                More4Rules.PLAYER_HUNGRY,
                More4Rules.PLAYER_TOOL_BROKEN,
                More4Rules.WRONG_TOOL_HAND,
                More4Rules.PORTAL_FOUND,
                More4Rules.PORTAL_UNSAFE,
                More4Rules.TAMED_PET,
                More4Rules.RARE_FOUND,
                More4Rules.RARE_MIXED,
                More4Rules.CAUTION)) {
            GraphPlan plan =
                    GraphPlanner.plan(GRAPH, GraphWorld.empty().event(event).state());
            assertTrue(
                    plan.tasks().stream().anyMatch(task -> task.id().startsWith("event:" + event + ":")),
                    "Событие области обязано дать задание: " + event
                            + " Иначе тест eventTask_EveryAreaEvent_GivesPlannedWork перестанет ловить эту поломку. При смене eventTask_EveryAreaEvent_GivesPlannedWork проверь это утверждение.");
        }
    }

    @Test
    void noMore4Event_WithoutInput_NoAreaWork() {
        GraphPlan plan = GraphPlanner.plan(GRAPH, GraphWorld.settled().state());

        for (String way : List.of(
                "more4_guard_settlement",
                "more4_bring_player_food",
                "more4_switch_tool",
                "more4_fence_portal",
                "more4_guard_with_pet",
                "more4_store_rare",
                "more4_feed_hungry")) {
            assertTrue(
                    plan.task(way).isEmpty(),
                    "Без входа области задачи нет: " + way
                            + " Иначе тест noMore4Event_WithoutInput_NoAreaWork перестанет ловить эту поломку. При смене noMore4Event_WithoutInput_NoAreaWork проверь это утверждение.");
        }
    }
}
