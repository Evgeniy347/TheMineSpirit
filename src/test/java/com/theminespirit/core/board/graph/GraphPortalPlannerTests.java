package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import org.junit.jupiter.api.Test;

class GraphPortalPlannerTests {
    @Test
    void plan_ObsidianShortStageThree_CastObsidianImplementedAndPosted() {
        GraphWorld world = GraphWorld.settled()
                .tools("spirit-1", 3)
                .with("water_bucket", 1)
                .with("obsidian", 0)
                .built("portal_hall", Built.PLANNED);
        world.stage = 3;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("cast_obsidian")
                        .map(task -> task.implemented() && task.feasible())
                        .orElse(false),
                "Обсидиана нет, есть ведро воды, алмазная кирка и шахта — задание «получить обсидиан» "
                        + "выполнимо и реализовано: " + plan.task("cast_obsidian"));
        assertTrue(
                GraphTickets.of(world.graph, world.state(), plan).post().stream()
                        .anyMatch(ticket -> "graph:cast_obsidian".equals(ticket.id())),
                "Задание на обсидиан уходит на доску, а не висит в списке «механик нет»: "
                        + GraphTickets.of(world.graph, world.state(), plan).post());
    }

    @Test
    void plan_PortalHallUnfinished_PortalLitNotFeasible() {
        GraphWorld world = GraphWorld.settled()
                .with("flint_and_steel", 1)
                .built("portal_hall", Built.PLANNED)
                .built("portal_lit", Built.PLANNED);
        world.stage = 3;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("building:portal_lit").map(task -> !task.feasible()).orElse(false),
                "Зал портала не готов на 100% — портал не зажигается: " + plan.task("building:portal_lit"));
        assertFalse(
                GraphTickets.of(world.graph, world.state(), plan).post().stream()
                        .anyMatch(ticket -> "graph:building:portal_lit".equals(ticket.id())),
                "Невыполнимое зажигание портала не ставится на доску");
    }

    @Test
    void plan_PortalHallBuiltFlintHeld_PortalLitImplementedAndFeasible() {
        GraphWorld world = GraphWorld.settled()
                .with("flint_and_steel", 1)
                .built("portal_hall", Built.BUILT)
                .built("portal_lit", Built.PLANNED);
        world.stage = 3;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("building:portal_lit")
                        .map(task -> task.implemented() && task.feasible())
                        .orElse(false),
                "Зал готов и огниво есть — задание «зажечь портал» выполнимо и реализовано: "
                        + plan.task("building:portal_lit"));
    }
}
