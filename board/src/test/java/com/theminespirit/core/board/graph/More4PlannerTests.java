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
        assertTrue(task.implemented(), "Охрана поселения подключена к действию");
        assertTrue(task.priority() > 0, "Игрок у поселения — заметный приоритет, а не ноль");

        GraphTickets.Result tickets = GraphTickets.of(GRAPH, state, plan);
        assertTrue(
                tickets.post().stream().anyMatch(ticket -> (GraphTickets.PREFIX + NEAR).equals(ticket.id())),
                "Реализованное действие ставится на доску: дух обязан взять охрану");
    }

    @Test
    void eventSpaceFound_TunesBuildingChoice_NotSeparateWork() {
        GraphState state = GraphWorld.empty().event(More4Rules.SPACE_FOUND).state();
        GraphPlan plan = GraphPlanner.plan(GRAPH, state);

        assertTrue(
                plan.tasks().stream().noneMatch(task -> task.id().startsWith("event:" + More4Rules.SPACE_FOUND + ":")),
                "Свободное место — условие выбора постройки, а не работа духа");
        assertTrue(
                GRAPH.way("more4_plan_expansion").isEmpty()
                        && GRAPH.way("more4_find_space").isEmpty(),
                "Пути-заглушки расширения удалены: поправка живёт в graph.json.choice");
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
                    "Событие области обязано дать задание: " + event);
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
            assertTrue(plan.task(way).isEmpty(), "Без входа области задачи нет: " + way);
        }
    }
}
