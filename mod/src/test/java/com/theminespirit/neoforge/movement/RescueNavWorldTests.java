package com.theminespirit.neoforge.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.navigation.NavActionCosts;
import com.theminespirit.core.navigation.NavWorld;
import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class RescueNavWorldTests {
    private static final BlockCoord START = new BlockCoord(0, 64, 0);
    private static final BlockCoord PLAN = new BlockCoord(1, 64, 0);

    @Test
    void passable_PlanCell_StaysWalkable() {
        RescueNavWorld world = new RescueNavWorld(new Open(), START, PLAN::equals);

        assertTrue(
                world.passable(PLAN.x(), PLAN.y(), PLAN.z()),
                "Клетка чужого плана проходима: это ходьба, не ломка"
                        + " Иначе тест passable_PlanCell_StaysWalkable перестанет ловить эту поломку. При смене passable_PlanCell_StaysWalkable проверь это утверждение.");
        assertTrue(
                world.walkable(PLAN.x(), PLAN.y(), PLAN.z()),
                "По чужому плану можно шагать, не меняя мир"
                        + " Иначе тест passable_PlanCell_StaysWalkable перестанет ловить эту поломку. При смене passable_PlanCell_StaysWalkable проверь это утверждение.");
        assertEquals(
                NavActionCosts.COST_INF,
                world.breakTicks(PLAN.x(), PLAN.y(), PLAN.z()),
                "Чужой план нельзя планировать к сломке: иначе маршрут спасения снимает стену дома"
                        + " При смене passable_PlanCell_StaysWalkable проверь это утверждение.");
    }

    @Test
    void startCell_PlannedCell_StaysPassable() {
        RescueNavWorld world = new RescueNavWorld(new Open(), START, START::equals);

        assertTrue(
                world.passable(START.x(), START.y(), START.z()),
                "Стартовая клетка не запрещается: дух уже в ней"
                        + " Иначе тест startCell_PlannedCell_StaysPassable перестанет ловить эту поломку. При смене startCell_PlannedCell_StaysPassable проверь это утверждение.");
    }

    @Test
    void freeCell_OutsidePlan_DelegatesToLiveWorld() {
        RescueNavWorld world = new RescueNavWorld(new Open(), START, PLAN::equals);

        assertTrue(
                world.passable(2, 64, 0),
                "Свободная клетка остаётся проходимой: запрет не глушит навигацию"
                        + " Иначе тест freeCell_OutsidePlan_DelegatesToLiveWorld перестанет ловить эту поломку. При смене freeCell_OutsidePlan_DelegatesToLiveWorld проверь это утверждение.");
        assertEquals(
                7.0,
                world.breakTicks(2, 64, 0),
                "Срок ломки свободной клетки берётся у живого мира"
                        + " Иначе тест freeCell_OutsidePlan_DelegatesToLiveWorld перестанет ловить эту поломку. При смене freeCell_OutsidePlan_DelegatesToLiveWorld проверь это утверждение.");
    }

    private static final class Open implements NavWorld {
        @Override
        public boolean available(int x, int z) {
            return true;
        }

        @Override
        public boolean passable(int x, int y, int z) {
            return true;
        }

        @Override
        public boolean walkable(int x, int y, int z) {
            return true;
        }

        @Override
        public boolean water(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean climbable(int x, int y, int z) {
            return false;
        }

        @Override
        public double breakTicks(int x, int y, int z) {
            return 7.0;
        }

        @Override
        public int minY() {
            return -64;
        }

        @Override
        public int maxY() {
            return 320;
        }
    }
}
