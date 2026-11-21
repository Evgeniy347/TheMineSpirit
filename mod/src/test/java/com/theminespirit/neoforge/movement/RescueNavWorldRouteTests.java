package com.theminespirit.neoforge.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.navigation.NavActionCosts;
import com.theminespirit.core.navigation.NavContext;
import com.theminespirit.core.navigation.NavGoalNear;
import com.theminespirit.core.navigation.NavSearchStatus;
import com.theminespirit.core.navigation.NavSegmentedRoute;
import com.theminespirit.core.navigation.NavSegmentedSession;
import com.theminespirit.core.navigation.NavSettings;
import com.theminespirit.core.navigation.NavWorld;
import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class RescueNavWorldRouteTests {
    private static final BlockCoord START = new BlockCoord(0, 64, 0);
    private static final BlockCoord PLAN = new BlockCoord(1, 64, 0);
    private static final BlockCoord GOAL = new BlockCoord(16, 64, 0);

    @Test
    void passable_ForeignPlanCell_StaysWalkableButUnbreakable() {
        NavWorld world = RescueNavWorld.route(new Open(), START, GOAL, PLAN::equals);

        assertTrue(
                world.passable(PLAN.x(), PLAN.y(), PLAN.z()),
                "Клетка чужого плана проходима, если она воздух: иначе дух, стоящий внутри плана, не находит "
                        + "даже соседнюю клетку (observe25 B4)"
                        + " При смене passable_ForeignPlanCell_StaysWalkableButUnbreakable проверь это утверждение.");
        assertTrue(
                world.walkable(PLAN.x(), PLAN.y() - 1, PLAN.z()),
                "По плану можно идти, не меняя мир"
                        + " Иначе тест passable_ForeignPlanCell_StaysWalkableButUnbreakable перестанет ловить эту поломку. При смене passable_ForeignPlanCell_StaysWalkableButUnbreakable проверь это утверждение.");
        assertEquals(
                NavActionCosts.COST_INF,
                world.breakTicks(PLAN.x(), PLAN.y(), PLAN.z()),
                "Ломать чужой план по-прежнему нельзя: охрана плана остаётся у ломки, а не у ходьбы"
                        + " Иначе тест passable_ForeignPlanCell_StaysWalkableButUnbreakable перестанет ловить эту поломку. При смене passable_ForeignPlanCell_StaysWalkableButUnbreakable проверь это утверждение.");
    }

    @Test
    void plan_RouteInsideBuildingFootprint_IsFound() {
        NavWorld world =
                RescueNavWorld.route(new Open(), START, GOAL, cell -> insidePlan(cell.x(), cell.y(), cell.z()));

        NavSegmentedRoute route = session(world).step(Long.MAX_VALUE);

        assertEquals(
                NavSearchStatus.FOUND,
                route.status(),
                "Маршрут с ровного места внутри плана находится: раньше все соседние клетки плана считались "
                        + "стеной, и A* отдавал NONE после одного узла (observe25 B4)"
                        + " Иначе тест plan_RouteInsideBuildingFootprint_IsFound перестанет ловить эту поломку. При смене plan_RouteInsideBuildingFootprint_IsFound проверь это утверждение.");
    }

    @Test
    void route_StartCell_StaysPassable() {
        NavWorld world = RescueNavWorld.route(new Open(), START, GOAL, START::equals);

        assertTrue(
                world.passable(START.x(), START.y(), START.z()),
                "Дух уже стоит на стартовой клетке плана"
                        + " Иначе тест route_StartCell_StaysPassable перестанет ловить эту поломку. При смене route_StartCell_StaysPassable проверь это утверждение.");
    }

    @Test
    void route_GoalCell_AllowedAsEntrance() {
        NavWorld world = RescueNavWorld.route(new Open(), START, PLAN, PLAN::equals);

        assertTrue(
                world.passable(PLAN.x(), PLAN.y(), PLAN.z()),
                "Вход в план — цель задания: его нельзя запирать запретом клеток (debtnav, п.6)"
                        + " Иначе тест route_GoalCell_AllowedAsEntrance перестанет ловить эту поломку. При смене route_GoalCell_AllowedAsEntrance проверь это утверждение.");
    }

    private static boolean insidePlan(int x, int y, int z) {
        return x >= -8 && x <= 8 && z >= -8 && z <= 8 && y >= 64 && y <= 66;
    }

    private static NavSegmentedSession session(NavWorld world) {
        return new NavSegmentedSession(
                new NavContext(world, new NavSettings(10_000, 10_000, 200_000, 50, 3, 12, 2, 20, 2, false, false), 64),
                START,
                new NavGoalNear(GOAL.x(), GOAL.y(), GOAL.z(), 1),
                32,
                6,
                0.5);
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
