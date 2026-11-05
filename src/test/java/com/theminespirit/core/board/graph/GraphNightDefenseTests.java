package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import org.junit.jupiter.api.Test;

class GraphNightDefenseTests {

    @Test
    void plan_NightAndFarTree_DoesNotGiveDistantGather() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 0).with("oak_planks", 0);
        world.distances.put("tree:oak", 80);
        world.distances.put("tree:jungle", 80);
        world.event("night");

        GraphPlan plan = world.plan();

        assertFalse(
                plan.tasks().stream().anyMatch(task -> "oak_log".equals(task.makes()) && task.feasible()),
                "Ночью граф не выдаёт далёкую добычу: иначе NightLeashGate провалит задачу на подходе. Задания: "
                        + GraphCaseFixtures.ids(plan));
    }

    @Test
    void plan_DayAndNearTree_GivesGather() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 0).with("oak_planks", 0);
        world.distances.put("tree:oak", 8);
        world.distances.put("tree:jungle", 8);

        GraphPlan plan = world.plan();

        assertTrue(
                plan.tasks().stream().anyMatch(task -> "oak_log".equals(task.makes()) && task.feasible()),
                "Днём ближнее дерево — обычная работа графа: " + GraphCaseFixtures.ids(plan));
    }

    @Test
    void plan_ExpeditionReady_GivesExpeditionNode() {
        GraphWorld world = GraphWorld.settled().event("expedition_ready");

        GraphPlan plan = world.plan();

        assertTrue(
                plan.tasks().stream()
                        .anyMatch(task -> "event:expedition_ready:expedition".equals(task.id())
                                && task.boardCase() == BoardCase.ACTIVITY_EXPEDITION
                                && task.feasible()),
                "Поход выдаёт только узел графа expedition, а не контроллер: " + GraphCaseFixtures.ids(plan));
    }
}
