// lib: Из Baritone (github.com/cabaletta/baritone, pathing/movement/ActionCostsTest.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NavActionCostsTest {
    @Test
    void fallCosts_TableRoundTrip_ReturnsOriginalBlocks() {
        assertEquals(4097, NavActionCosts.FALL_N_BLOCKS_COST.length, "таблица падений покрывает 0…4096 блоков");
        for (int blocks = 0; blocks < 4097; blocks++) {
            assertEquals(
                    blocks,
                    ticksToBlocks(NavActionCosts.FALL_N_BLOCKS_COST[blocks]),
                    0.00000000001,
                    "падение на " + blocks + " блоков переводится в тики и обратно");
        }
        assertEquals(6.2344, NavActionCosts.FALL_1_25_BLOCKS_COST, 0.00001, "падение 1.25 блока");
        assertEquals(3.0710, NavActionCosts.FALL_0_25_BLOCKS_COST, 0.00001, "падение 0.25 блока");
        assertEquals(3.1634, NavActionCosts.JUMP_ONE_BLOCK_COST, 0.00001, "прыжок на блок");
    }

    private static double ticksToBlocks(double ticks) {
        double fallDistance = 0;
        int integral = (int) Math.floor(ticks);
        for (int tick = 0; tick < integral; tick++) {
            fallDistance += NavActionCosts.velocity(tick);
        }
        double partial = ticks - Math.floor(ticks);
        fallDistance += NavActionCosts.velocity(integral) * partial;
        return fallDistance;
    }
}
