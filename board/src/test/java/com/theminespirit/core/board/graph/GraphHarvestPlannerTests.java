package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GraphHarvestPlannerTests {
    @Test
    void plan_NoSaplings_AllSpeciesGatherImplemented() {
        GraphWorld world = GraphWorld.settled();
        List<String> species = List.of("oak", "birch", "spruce", "jungle", "acacia", "dark_oak", "cherry", "mangrove");
        for (String tree : species) {
            world.with(tree + "_sapling", 0);
            world.world.add("tree:" + tree);
        }
        GraphPlan plan = world.plan();

        for (String tree : species) {
            String way = "gather_" + tree + "_sapling";
            assertTrue(
                    plan.task(way).filter(GraphPlan.Task::implemented).isPresent(),
                    "Нет саженцев " + tree + " — путь " + way + " есть и реализован: " + plan.task(way));
        }
    }

    @Test
    void plan_NoApples_CollectAppleImplemented() {
        GraphPlan plan = GraphWorld.settled().with("apple", 0).plan();

        assertTrue(
                plan.task("collect_apple").filter(GraphPlan.Task::implemented).isPresent(),
                "Нет яблок — путь collect_apple есть и реализован: " + plan.task("collect_apple"));
    }

    @Test
    void plan_NoStrippedLogs_StripImplemented() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 0);
        world.stage = 2;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("strip_oak_log").filter(GraphPlan.Task::implemented).isPresent(),
                "Нет обтёсанных брёвен — путь strip_oak_log есть и реализован: " + plan.task("strip_oak_log"));
    }
}
