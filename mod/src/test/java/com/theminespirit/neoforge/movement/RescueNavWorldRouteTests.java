package com.theminespirit.neoforge.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.navigation.NavWorld;
import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class RescueNavWorldRouteTests {
    private static final BlockCoord START = new BlockCoord(0, 64, 0);
    private static final BlockCoord PLAN = new BlockCoord(1, 64, 0);
    private static final BlockCoord GOAL = new BlockCoord(2, 64, 0);

    @Test
    void route_ForeignPlanCell_BlockedForAnyRoute() {
        NavWorld world = RescueNavWorld.route(new Open(), START, GOAL, PLAN::equals);

        assertFalse(
                world.passable(PLAN.x(), PLAN.y(), PLAN.z()),
                "Общий маршрут, а не только спасение, не заходит в чужую клетку плана: иначе самоспасение "
                        + "ломает стену постройки (debtnav, п.6)");
    }

    @Test
    void route_StartCell_StaysPassable() {
        NavWorld world = RescueNavWorld.route(new Open(), START, GOAL, START::equals);

        assertTrue(world.passable(START.x(), START.y(), START.z()), "Дух уже стоит на стартовой клетке плана");
    }

    @Test
    void route_GoalCell_AllowedAsEntrance() {
        NavWorld world = RescueNavWorld.route(new Open(), START, PLAN, PLAN::equals);

        assertTrue(
                world.passable(PLAN.x(), PLAN.y(), PLAN.z()),
                "Вход в план — цель задания: его нельзя запирать запретом клеток (debtnav, п.6)");
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
