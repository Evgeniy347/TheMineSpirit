package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import org.junit.jupiter.api.Test;

class BrewGraphPlannerTests {

    @Test
    void ways_StageThreeBrewing_AllImplemented() {
        List<String> ids = List.of(
                "brewing_stand",
                "grind_blaze_powder",
                "harvest_nether_wart",
                "brew_healing",
                "brew_fire_resistance",
                "brew_regeneration",
                "brew_strength",
                "brew_swiftness",
                "brew_night_vision",
                "brew_water_breathing",
                "splash_healing",
                "splash_fire_resistance",
                "splash_regeneration",
                "splash_strength",
                "splash_swiftness",
                "splash_night_vision",
                "splash_water_breathing");

        assertTrue(
                ids.stream().allMatch(GraphWorld.GRAPH::implemented),
                "Зельеварение реализовано: с путей и стойки этапа 3 снята пометка implemented=false");
    }

    @Test
    void ways_StageThreeBrewing_RequireGlassBottlesAndFuel() {
        SupplyGraph.Way healing = GraphWorld.GRAPH.way("brew_healing").orElseThrow();

        assertEquals(3, healing.consumes().get("glass_bottle"), "Лечение варит три бутылки за цикл: " + healing);
        assertEquals(2, healing.consumes().get("blaze_powder"), "Два шага варки — два огненных порошка: " + healing);
        assertEquals("brewing_stand", healing.building(), "Варка идёт только в варочной стойке: " + healing);
    }

    @Test
    void plan_StageThreePotionNoStand_NotFeasible() {
        GraphWorld world = GraphWorld.settled()
                .with("glass_bottle", 8)
                .with("nether_wart", 8)
                .with("magma_cream", 2)
                .with("blaze_powder", 8)
                .event("lava_seen");
        world.stage = 3;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("brew_fire_resistance")
                        .map(task -> task.implemented() && !task.feasible())
                        .orElse(false),
                "Варка реализована, но без варочной стойки задание невыполнимо: " + plan.task("brew_fire_resistance"));
    }

    @Test
    void plan_StageThreePotionFullLine_Feasible() {
        GraphWorld world = GraphWorld.settled()
                .built("brewing_stand", Built.BUILT)
                .with("glass_bottle", 8)
                .with("nether_wart", 8)
                .with("ghast_tear", 2)
                .with("blaze_powder", 8)
                .with("gunpowder", 2);
        world.stage = 3;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("brew_regeneration")
                                .map(task -> task.implemented() && task.feasible())
                                .orElse(false)
                        && plan.task("splash_regeneration")
                                .map(task -> task.implemented() && task.feasible())
                                .orElse(false),
                "С варочной стойкой, бутылками и ингредиентами регенерация и взрывное зелье выполнимы: "
                        + plan.task("brew_regeneration") + ", " + plan.task("splash_regeneration"));
    }

    @Test
    void plan_NetherWartFarmNoSoulSand_NotFeasible() {
        GraphWorld world = GraphWorld.settled().with("nether_wart", 2);
        world.stage = 4;
        world.world.add("nether");
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("harvest_nether_wart")
                        .map(task -> task.implemented() && !task.feasible())
                        .orElse(false),
                "Грядка реализована, но без песка душ (только Незер) задание невыполнимо: "
                        + plan.task("harvest_nether_wart"));
    }

    @Test
    void plan_NetherWartFarmWithSeed_Feasible() {
        GraphWorld world = GraphWorld.settled().with("nether_wart", 2).with("soul_sand", 8);
        world.stage = 4;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("harvest_nether_wart")
                        .map(task -> task.implemented() && task.feasible())
                        .orElse(false),
                "С наростом в запасе и песком душ грядка выполнима: " + plan.task("harvest_nether_wart"));
    }
}
