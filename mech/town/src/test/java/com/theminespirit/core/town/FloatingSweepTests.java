package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FloatingSweepTests {
    @Test
    void cycleTicks_SettlementSizedSweep_FitsOneMinute() {
        int slices = 10 * 10 * 3;

        long ticks = FloatingSweep.cycleTicks(slices);

        assertTrue(
                ticks <= 1200,
                "Полный обход поселения обязан укладываться в минуту: иначе висящее после рубки замечается "
                        + "через десятки минут и крону не убирают; получено " + ticks + " тиков");
    }

    @Test
    void cycleTicks_NoSlices_IsZero() {
        assertEquals(0L, FloatingSweep.cycleTicks(0), "Пустой участок обходится за ноль тиков");
    }

    @Test
    void cycleTicks_Negative_IsRejected() {
        boolean failed = false;
        try {
            FloatingSweep.cycleTicks(-1);
        } catch (IllegalArgumentException expected) {
            failed = true;
        }
        assertTrue(failed, "Отрицательное число срезов — ошибка, а не тихий ноль");
    }
}
