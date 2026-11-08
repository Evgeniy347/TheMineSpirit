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
    void passable_PlanCell_WalkAllowedBreakBlocked() {
        RescueNavWorld world = new RescueNavWorld(new Open(), START, PLAN::equals);

        assertTrue(
                world.passable(PLAN.x(), PLAN.y(), PLAN.z()),
                "По клетке чужого плана можно пройти: запрет проходимости запирал духа в доме и отдавал "
                        + "NONE после 1–3 узлов (observe25 B4)");
        assertTrue(world.walkable(PLAN.x(), PLAN.y(), PLAN.z()), "По чужому плану можно шагать");
        assertEquals(
                NavActionCosts.COST_INF,
                world.breakTicks(PLAN.x(), PLAN.y(), PLAN.z()),
                "Чужой план нельзя планировать к сломке: иначе маршрут спасения снимает стену дома");
    }

    @Test
    void startCell_PlannedCell_StaysPassable() {
        RescueNavWorld world = new RescueNavWorld(new Open(), START, START::equals);

        assertTrue(world.passable(START.x(), START.y(), START.z()), "Стартовая клетка не запрещается: дух уже в ней");
    }

    @Test
    void freeCell_OutsidePlan_DelegatesToLiveWorld() {
        RescueNavWorld world = new RescueNavWorld(new Open(), START, PLAN::equals);

        assertTrue(world.passable(2, 64, 0), "Свободная клетка остаётся проходимой: запрет не глушит навигацию");
        assertEquals(7.0, world.breakTicks(2, 64, 0), "Срок ломки свободной клетки берётся у живого мира");
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
