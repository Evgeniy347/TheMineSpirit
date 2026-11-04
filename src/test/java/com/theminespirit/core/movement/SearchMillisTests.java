package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SearchMillisTests {
    @Test
    void since_ZeroStart_IsZeroNotHuge() {
        assertEquals(
                0,
                SearchMillis.since(0, 43_844_176_000_000L),
                "Незаполненный замер (0) не превращается в «поиск 43844176 мс»: в логе честный ноль");
    }

    @Test
    void since_RealStart_CountsMilliseconds() {
        assertEquals(
                250,
                SearchMillis.since(1_000_000_000L, 1_250_000_000L),
                "Замер от реального начала считается в миллисекундах");
    }

    @Test
    void since_ClockWentBack_IsZero() {
        assertEquals(
                0,
                SearchMillis.since(2_000_000_000L, 1_000_000_000L),
                "Обратный ход часов не даёт отрицательный поиск");
    }
}
