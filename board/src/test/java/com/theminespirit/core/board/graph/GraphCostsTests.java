package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GraphCostsTests {
    @Test
    void plan_FarJungle_RanksBelowNearOak() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 0).with("jungle_log", 0);
        world.distances.put("tree:jungle", 120);
        GraphPlan plan = world.plan();

        assertTrue(
                plan.priority("chop_jungle_log") < plan.priority("chop_oak_log")
                        && plan.task("chop_jungle_log").orElseThrow().ticks()
                                > plan.task("chop_oak_log").orElseThrow().ticks(),
                "Тропический лес в 120 блоках — рубка дольше и ниже ближнего дуба: " + plan.task("chop_jungle_log")
                        + " / " + plan.task("chop_oak_log"));
    }

    @Test
    void plan_FurnaceOverloaded_SecondFurnaceTask() {
        GraphWorld world = GraphWorld.settled()
                .with("oak_log", 40)
                .with("coal", 10)
                .with("raw_iron", 6)
                .with("iron_ingot", 0)
                .with("sand", 20)
                .with("glass", 0)
                .with("cactus", 4);
        world.stage = 3;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("station:furnace").isPresent(),
                "Плавок больше, чем тянет одна печь (2 задания на печь) — задание поставить ещё печь: "
                        + plan.tasks().stream()
                                .filter(task ->
                                        task.feasible() && task.consumes().containsKey("#coals"))
                                .map(GraphPlan.Task::id)
                                .toList());
    }

    @Test
    void plan_HutAndPlanks_HutTakesLonger() {
        GraphPlan plan = GraphWorld.settled().with("oak_planks", 0).plan();

        assertTrue(
                plan.task("building:hut").orElseThrow().ticks()
                        > plan.task("craft_oak_planks").orElseThrow().ticks(),
                "Хижина строится дольше, чем крафтятся доски: длительность задания из costs");
    }
}
