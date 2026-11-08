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
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class RescueNavWorldRouteTests {
    private static final BlockCoord START = new BlockCoord(0, 64, 0);
    private static final BlockCoord PLAN = new BlockCoord(1, 64, 0);
    private static final BlockCoord GOAL = new BlockCoord(2, 64, 0);

    @Test
    void route_ForeignPlanCell_BreakBlockedWalkAllowed() {
        NavWorld world = RescueNavWorld.route(new Open(), START, GOAL, PLAN::equals);

        assertTrue(
                world.passable(PLAN.x(), PLAN.y(), PLAN.z()),
                "Общий маршрут не запирает проход через чужой план: иначе дух в доме не выходит и получает "
                        + "NONE после 1–3 узлов (observe25 B4)");
        assertEquals(
                NavActionCosts.COST_INF,
                world.breakTicks(PLAN.x(), PLAN.y(), PLAN.z()),
                "Ломать чужой план по-прежнему нельзя: самоспасение не снимает стену постройки (debtnav, п.6)");
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

    @Test
    void route_SpiritInsideForeignHouse_ReachesYardWalk() {
        Predicate<BlockCoord> house = cell ->
                cell.x() >= -2 && cell.x() <= 3 && cell.z() >= -2 && cell.z() <= 2 && cell.y() >= 63 && cell.y() <= 65;
        NavWorld world = RescueNavWorld.route(new Open(), START, GOAL, house);
        NavSegmentedSession session = new NavSegmentedSession(
                new NavContext(world, new NavSettings(10_000, 10_000, 200_000, 50, 3, 12, 2, 20, 2, false, false), 0),
                START,
                new NavGoalNear(GOAL.x(), GOAL.y(), GOAL.z(), 0),
                32,
                6,
                0.5);

        NavSegmentedRoute route = session.step(Long.MAX_VALUE);

        assertEquals(
                NavSearchStatus.FOUND,
                route.status(),
                "дух стоит в чужом доме (клетки плана вокруг), цель — двор: маршрут обязан выйти, а не "
                        + "отдать NONE после 1 узла, как в observe25 B4 (spirit-1 в (-176,72,-510))");
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
