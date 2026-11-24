package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphStageOneBuildTests {
    @Test
    void plan_ActualBillBiggerThanGraphShare_HutAndStreetsStillStart() {
        GraphWorld world = GraphWorld.settled()
                .with("oak_log", 64)
                .with("oak_planks", 64)
                .with("cobblestone", 64)
                .with("dirt", 64);
        world.materials.put("hut", Map.of("oak_log", 120, "oak_planks", 900));
        world.materials.put("streets", Map.of("dirt", 800));

        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("building:hut").map(GraphPlan.Task::feasible).orElse(false)
                        && plan.task("building:streets").isPresent(),
                "Счёт схемы больше, чем граф успевает заготовить: стройка открывается по объявленной в графе "
                        + "доле, точный остаток подвозит FirstStepsRunner; хижина " + plan.task("building:hut")
                        + ", улицы " + plan.task("building:streets")
                        + " Иначе тест plan_ActualBillBiggerThanGraphShare_HutAndStreetsStillStart перестанет ловить эту поломку. При смене plan_ActualBillBiggerThanGraphShare_HutAndStreetsStillStart проверь это утверждение.");
    }

    @Test
    void plan_StageBuildingMissing_StaysOnBoardAndPressesSupply() {
        GraphWorld world = GraphWorld.settled().tools("spirit-1", -1);
        GraphPlan plan = world.plan();

        GraphPlan.Task hut = plan.task("building:hut").orElseThrow();
        assertTrue(
                hut.raised()
                        && plan.tasks().stream()
                                .filter(task -> hut.consumes().containsKey(task.makes()) && task.feasible())
                                .anyMatch(supply -> supply.priority() > hut.priority()),
                "Постройка этапа не вытесняется набором духа: она на доске, а выполнимая добыча её материала раньше "
                        + "самой постройки; кирка "
                        + "духу без инструментов — честный соперник хижине. " + CrossScene.ORDER_HINT + ": "
                        + plan.tasks()
                        + " Иначе тест plan_StageBuildingMissing_StaysOnBoardAndPressesSupply перестанет ловить эту поломку. При смене plan_StageBuildingMissing_StaysOnBoardAndPressesSupply проверь это утверждение.");
    }

    @Test
    void plan_ClearingInProgress_ClearingAndHutBothOnBoard() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 64).with("oak_planks", 64);
        world.buildings.put("clearing", GraphState.Built.PLANNED);
        world.buildings.remove("chests");

        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("building:clearing").isPresent()
                        && plan.task("building:hut").isPresent(),
                "Расчистка и хижина — сразу обе на доске: хижина ждёт площадку, но остаётся в плане "
                        + "до конца расчистки (владелец 28.10); расчистка " + plan.task("building:clearing")
                        + ", хижина " + plan.task("building:hut")
                        + " Иначе тест plan_ClearingInProgress_ClearingAndHutBothOnBoard перестанет ловить эту поломку. При смене plan_ClearingInProgress_ClearingAndHutBothOnBoard проверь это утверждение.");
        assertTrue(
                GraphWorld.GRAPH.buildings().get("clearing").advice().contains("settlement_single_y"),
                "Правило стройки этапа 1 записано в граф: всё поселение на одном y"
                        + " Иначе тест plan_ClearingInProgress_ClearingAndHutBothOnBoard перестанет ловить эту поломку. При смене plan_ClearingInProgress_ClearingAndHutBothOnBoard проверь это утверждение.");
    }
}
