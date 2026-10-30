package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReturnStallTests {
    private static final Point ANCHOR = new Point(0.5, 70, 0.5);

    @Test
    void stalled_NoProgressForLimit_ReportsStall() {
        ReturnStall stall = new ReturnStall();
        stall.stalled(ANCHOR, 10.0);

        for (int tick = 1; tick < ReturnStall.STALL_TICKS; tick++) {
            assertFalse(
                    stall.stalled(ANCHOR, 10.0),
                    "До предела срыва возврат обязан продолжаться: " + tick + " тик из " + ReturnStall.STALL_TICKS);
        }

        assertTrue(
                stall.stalled(ANCHOR, 10.0),
                "На " + ReturnStall.STALL_TICKS + "-м тике без прогресса возврат обязан сниматься: "
                        + "иначе дух навсегда тянется к недостижимому якорю");
    }

    @Test
    void stalled_Progress_ResetsCounter() {
        ReturnStall stall = new ReturnStall();
        for (int tick = 0; tick < ReturnStall.STALL_TICKS - 1; tick++) {
            stall.stalled(ANCHOR, 10.0);
        }

        stall.stalled(ANCHOR, 9.0);

        assertFalse(
                stall.stalled(ANCHOR, 9.0),
                "Сокращение расстояния на полблока обязано сбрасывать счётчик: иначе медленный, но живой "
                        + "возврат снимается раньше времени");
    }
}
