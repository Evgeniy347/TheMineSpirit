package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GraphAccessTests {
    @Test
    void plan_AccessBlocked_WalkwayAboveLevelingAndWork() {
        GraphWorld world = GraphWorld.settled().event("access_blocked").event("level_lot");
        GraphPlan plan = world.plan();
        int access = plan.priority("event:access_blocked:clear_access");

        assertTrue(
                access > plan.priority("event:level_lot:level_lot") && access >= GraphWorld.best(plan, "mine_"),
                "Нет прохода к сундукам и дверям — проход выше выравнивания по плану и обычной работы: "
                        + CrossScene.ranked(plan));
        assertTrue(
                access < GraphPriorities.life(GraphWorld.GRAPH),
                "Проход — верх полосы работы, ниже нужд и жизни: " + access);
    }
}
