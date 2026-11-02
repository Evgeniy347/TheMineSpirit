package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClimbReachTests {
    @Test
    void rise_TargetEightAbove_NeedsPillar() {
        int rise = ClimbReach.rise(63, 70);

        assertEquals(7, rise, "Цель на y=70 при духе y=63 — подъём на 7");
        assertTrue(ClimbReach.needed(rise), "8-блочная стоянка недостижима шагом: нужен столб (observe19 D)");
    }

    @Test
    void rise_SmallStep_DoesNotNeedPillar() {
        assertFalse(ClimbReach.needed(ClimbReach.rise(67, 70)), "Три блока дух берёт прыжком/шагом — столб не нужен");
    }

    @Test
    void rise_TargetBelow_IsZero() {
        assertEquals(0, ClimbReach.rise(70, 63), "Цель ниже духа — подниматься некуда");
        assertFalse(ClimbReach.needed(0), "Нулевой подъём не требует столба");
    }

    @Test
    void rise_NegativeRise_IsRejectedByNeeded() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ClimbReach.needed(-1),
                "Отрицательный подъём — ошибка вызывающего, а не тихий столб вниз");
    }
}
