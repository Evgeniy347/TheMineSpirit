package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphCropTests {
    private static GraphWorld stageTwo() {
        GraphWorld world = GraphWorld.settled();
        world.stage = 2;
        return world;
    }

    @Test
    void plan_NoWildCarrots_NoForageAndNoCarrotField() {
        GraphPlan plan = stageTwo().with("carrot", 0).plan();

        assertFalse(
                plan.raised("forage_carrot") || plan.raised("building:field_carrot"),
                "Диких посевов моркови рядом нет и моркови нет — собирать негде, поле не сажать нечем: "
                        + CrossScene.ranked(plan));
    }

    @Test
    void plan_WildCarrotsNear_ForageRaised() {
        GraphWorld world = stageTwo().with("carrot", 0);
        world.world.add("crop:carrot");

        assertTrue(
                world.plan().raised("forage_carrot"),
                "Дикие посевы моркови рядом — морковь собирается: " + CrossScene.ranked(world.plan()));
    }

    @Test
    void plan_BeetrootSeedsHeld_FieldPlantedFromSeeds() {
        GraphPlan plan = stageTwo().with("beetroot_seeds", 4).plan();

        assertTrue(
                plan.notices().stream().anyMatch(notice -> "field_beetroot".equals(notice.building())),
                "Семена свёклы есть — стратегу просьба внести поле свёклы в план: " + plan.notices());
        assertEquals(
                Map.of("beetroot_seeds", 8, "dirt", 16),
                GraphWorld.GRAPH.buildings().get("field_beetroot").materials(),
                "Свёклу сажают семенами свёклы, а не корнеплодом");
    }
}
