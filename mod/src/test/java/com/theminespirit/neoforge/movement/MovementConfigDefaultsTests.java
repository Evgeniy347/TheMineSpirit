package com.theminespirit.neoforge.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MovementConfigDefaultsTests {
    @Test
    void defaults_MovementDepths_ArePositiveAndOrdered() {
        assertEquals(4, MovementConfig.DEFAULT_GROUND_SEARCH_DEPTH, "поиск земли вглубь по умолчанию — 4 блока");
        assertEquals(2, MovementConfig.DEFAULT_SURVEY_DEPTH, "вертикальный осмотр по умолчанию — 2 блока");
        assertEquals(2, MovementConfig.DEFAULT_SWIM_DEPTH, "глубина брода по умолчанию — 2 блока");
        assertTrue(
                MovementConfig.DEFAULT_GROUND_SEARCH_DEPTH > MovementConfig.DEFAULT_SWIM_DEPTH,
                "землю ищут глубже, чем порог брода: иначе дух ищет опору в воде");
    }
}
