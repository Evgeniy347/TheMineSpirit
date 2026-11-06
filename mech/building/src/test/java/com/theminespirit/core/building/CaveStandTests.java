package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CaveStandTests {
    @Test
    void cave_DeepWithoutSky_Cave() {
        assertTrue(
                CaveStand.cave(64, 71, false),
                "Стоянка на 7 ниже ящика без неба — старая пещера под участком: туда не идти (INC-67)");
    }

    @Test
    void cave_OpenLowlandWithSky_NotCave() {
        assertFalse(
                CaveStand.cave(68, 71, true),
                "Открытая низина у края участка (небо видно) — законная стоянка: «убрано 0 из 1088» (INC-68)");
    }
}
