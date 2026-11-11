package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphStageOneBuildTests {
    @Test
    void plan_ActualBillBiggerThanGraphShare_FenceAndHutStillStart() {
        GraphWorld world =
                GraphWorld.settled().with("oak_log", 64).with("oak_planks", 64).with("cobblestone", 64);
        world.materials.put("fence", Map.of("oak_log", 800));
        world.materials.put("hut", Map.of("oak_log", 120, "oak_planks", 900));

        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("building:fence").map(GraphPlan.Task::feasible).orElse(false)
                        && plan.task("building:hut")
                                .map(GraphPlan.Task::feasible)
                                .orElse(false),
                "Счёт схемы (800 брёвен у ограды, 1020 у хижины) больше, чем граф успевает заготовить: "
                        + "стройка открывается по объявленной в графе доле, точный остаток подвозит FirstStepsRunner; "
                        + "ограда " + plan.task("building:fence") + ", хижина " + plan.task("building:hut"));
    }

    @Test
    void plan_StageBuildingMissing_StaysOnBoardAndPressesSupply() {
        GraphWorld world = GraphWorld.settled().tools("spirit-1", -1);
        GraphPlan plan = world.plan();

        GraphPlan.Task fence = plan.task("building:fence").orElseThrow();
        assertTrue(
                fence.raised()
                        && plan.tasks().stream()
                                .filter(task -> fence.consumes().containsKey(task.makes()) && task.feasible())
                                .anyMatch(supply -> supply.priority() > fence.priority()),
                "Ограда этапа не вытесняется набором духа: она на доске, а выполнимая добыча её материала раньше "
                        + "самой ограды; кирка "
                        + "духу без инструментов — честный соперник ограде. " + CrossScene.ORDER_HINT + ": "
                        + plan.tasks());
    }

    @Test
    void plan_ClearingInProgress_FenceAndClearingBothOnBoard() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 64).with("oak_planks", 64);
        world.buildings.put("clearing", GraphState.Built.PLANNED);
        world.buildings.remove("chests");

        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("building:clearing").isPresent()
                        && plan.task("building:fence")
                                .map(GraphPlan.Task::feasible)
                                .orElse(false),
                "Расчистка и ограда — сразу обе на доске: ограда встаёт там, где место уже свободно, не дожидаясь "
                        + "конца расчистки (владелец 28.10); расчистка " + plan.task("building:clearing")
                        + ", ограда " + plan.task("building:fence"));
        assertTrue(
                GraphWorld.GRAPH.buildings().get("clearing").advice().contains("settlement_single_y")
                        && GraphWorld.GRAPH.buildings().get("fence").advice().contains("build_fence_where_cell_free"),
                "Правила стройки этапа 1 записаны в граф: всё поселение на одном y, ограда — на освободившейся клетке");
    }
}
