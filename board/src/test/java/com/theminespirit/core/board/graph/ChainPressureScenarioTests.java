package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossScene.ORDER_HINT;
import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.ids;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageTwo;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChainPressureScenarioTests {

    @Test
    void hutWithoutWood_PressesChoppingAndPlanks_HutItselfStaysBehind() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 0).with("oak_planks", 0);
        GraphPlan plan = world.plan();

        GraphPlan.Task hut = task(plan, "building:hut");
        GraphPlan.Task chop = task(plan, "chop_oak_log");
        GraphPlan.Task planks = task(plan, "craft_oak_planks");
        assertTrue(
                chop.priority() > hut.priority() && planks.priority() > hut.priority(),
                "Хижина без дерева сама невелика, но давит на рубку и доски: они раньше хижины. " + ORDER_HINT + ": "
                        + plan.tasks());
    }

    @Test
    void orchard_WoodLacking_IsRaised() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 0).with("oak_planks", 0);
        GraphPlan plan = world.plan();

        assertTrue(
                plan.raised("building:orchard_oak"),
                "Нехватка дерева давит на сад: без сада рубить скоро будет нечего. " + ORDER_HINT + ": " + ids(plan));
    }

    @Test
    void mineIron_OreLacking_AheadOfMineBuilding() {
        GraphWorld world = stageTwo().with("iron_ingot", 0).with("raw_iron", 0);
        GraphPlan plan = plan(world, facts(named(spirit("spirit-1"), spirit("spirit-2"))));

        GraphPlan.Task iron = task(plan, "mine_iron");
        assertTrue(iron.raised(), "Нехватка железа поднимает добычу руды: " + ids(plan));
        plan.task("building:mine")
                .ifPresent(mine -> assertTrue(
                        mine.priority() < iron.priority(),
                        "Шахта как постройка невелика, давление идёт в добычу руды. " + ORDER_HINT + ": "
                                + plan.tasks()));
    }
}
