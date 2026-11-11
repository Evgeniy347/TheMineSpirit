package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DirtSupplyTests {
    @Test
    void plan_DirtShortForStreets_PostsDigDirt() {
        GraphPlan plan = GraphWorld.empty()
                .with("dirt", 3)
                .needs("streets", "dirt", 399)
                .world("soil")
                .plan();

        Optional<GraphPlan.Task> dig = plan.task("dig_dirt");
        assertTrue(
                dig.isPresent()
                        && dig.orElseThrow().raised()
                        && dig.orElseThrow().feasible(),
                "При нехватке земли для улиц граф обязан ставить добычу земли, получено " + plan.tasks());
    }

    @Test
    void plan_QuarryInFortyBlocks_StillPostsDigDirt() {
        GraphPlan plan = GraphWorld.empty()
                .with("dirt", 3)
                .needs("streets", "dirt", 399)
                .world("soil")
                .distance("soil", 40)
                .plan();

        Optional<GraphPlan.Task> dig = plan.task("dig_dirt");
        assertTrue(
                dig.isPresent() && dig.orElseThrow().raised(),
                "Карьер в 40 блоках — достижимый источник земли: задание добычи обязано остаться, получено "
                        + plan.tasks());
    }

    @Test
    void plan_NoSoilAround_ReportsMissingSourceInsteadOfDigging() {
        GraphPlan plan = GraphWorld.empty()
                .with("dirt", 3)
                .needs("streets", "dirt", 399)
                .noWorld("soil")
                .plan();

        Optional<GraphPlan.Task> dig = plan.task("dig_dirt");
        assertFalse(
                dig.isPresent() && dig.orElseThrow().raised(),
                "Без дёрна вне участка добыча земли невыполнима: задание не ставится, получено " + plan.tasks());
        assertTrue(
                plan.notices().stream()
                        .anyMatch(notice -> "dirt".equals(notice.building())
                                && notice.reason().contains("нет источника")
                                && notice.reason().contains("soil")),
                "Нет источника земли — честный факт для стратега с причиной, получено " + plan.notices());
    }
}
