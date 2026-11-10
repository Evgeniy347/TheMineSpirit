package com.theminespirit.neoforge.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.navigation.NavActionCosts;
import com.theminespirit.core.navigation.NavWorld;
import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class OwnBreakNavWorldTests {
    private static final BlockCoord OWN = new BlockCoord(1, 64, 0);
    private static final BlockCoord OTHER = new BlockCoord(2, 64, 0);

    @Test
    void breakTicks_OwnBlock_StaysBreakable() {
        NavWorld world = new OwnBreakNavWorld(new Open(), OWN::equals);

        assertEquals(7.0, world.breakTicks(OWN.x(), OWN.y(), OWN.z()), "Свой временный блок дух вправе сломать");
    }

    @Test
    void breakTicks_ForeignBlock_IsUnbreakable() {
        NavWorld world = new OwnBreakNavWorld(new Open(), OWN::equals);

        assertEquals(
                NavActionCosts.COST_INF,
                world.breakTicks(OTHER.x(), OTHER.y(), OTHER.z()),
                "Чужой блок остаётся неразрушимым: маршрут не сносит постройки и план поселения");
    }

    @Test
    void passable_FreeCell_DelegatesToLiveWorld() {
        NavWorld world = new OwnBreakNavWorld(new Open(), OWN::equals);

        assertTrue(world.passable(OTHER.x(), OTHER.y(), OTHER.z()), "Ходьба не запрещается: ломка отдельно");
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
