package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import org.junit.jupiter.api.Test;

class FarmGraphTests {
    @Test
    void plan_PenAbsentAtStageTwo_GraphDemandsPen() {
        GraphWorld world = GraphWorld.settled().built("pen_cow", Built.PLANNED);
        world.stage = 2;

        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("building:pen_cow").isPresent(),
                "Загон — узел графа: без сторожей работа выбирается постройкой building:pen_cow — " + plan.tasks());
    }

    @Test
    void plan_PenBuilt_CaptureCowIsWork() {
        GraphWorld world = GraphWorld.settled().built("pen_cow", Built.BUILT).with("herd_cow", 0);
        world.stage = 2;

        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("capture_cow").map(GraphPlan.Task::feasible).orElse(false),
                "Загон построен и коров мало — граф выбирает ловлю коровы: " + plan.task("capture_cow"));
    }

    @Test
    void plan_SheepInSheepPen_ShearingIsWork() {
        GraphWorld world = GraphWorld.settled()
                .built("pen_sheep", Built.BUILT)
                .with("herd_sheep", 6)
                .with("white_wool", 0);
        world.stage = 2;

        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("shear").map(GraphPlan.Task::feasible).orElse(false),
                "Овчарня и овцы есть — граф выбирает стрижку: " + plan.task("shear"));
    }

    @Test
    void plan_StringsStocked_WeaveWoolIsWork() {
        GraphWorld world = GraphWorld.settled().with("string", 32).with("white_wool", 0);
        world.stage = 2;

        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("weave_wool").map(GraphPlan.Task::feasible).orElse(false),
                "Нити есть, шерсти нет — граф выбирает тканьё шерсти: " + plan.task("weave_wool"));
    }
}
