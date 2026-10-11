package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GotoTuningTests {
    @Test
    void timeoutTicks_KnownDistance_AddsReserveToTicksPerBlock() {
        assertEquals(280, GotoTuning.timeoutTicks(2),
                "Тайм-аут обязан быть 40*2+200 = 280 тиков: фиксированные 300 тиков отсекали бы дальнюю цель "
                        + "раньше, чем дух успеет дойти");
    }

    @Test
    void timeoutTicks_DistanceIncreasesTimeout_Linearly() {
        assertEquals(600, GotoTuning.timeoutTicks(10),
                "Тайм-аут обязан расти с дистанцией: 40*10+200 = 600 тиков, иначе дальний goto сдаётся на полпути");
    }

    @Test
    void timeoutTicks_ZeroDistance_ReturnsReserve() {
        assertEquals(200, GotoTuning.timeoutTicks(0),
                "Нулевая дистанция оставляет только запас: цель рядом, но дух должен успеть дойти");
    }

    @Test
    void timeoutTicks_HugeDistance_ClampsToIntegerMax() {
        assertEquals(Integer.MAX_VALUE, GotoTuning.timeoutTicks(100_000_000),
                "Огромная дистанция не должна переполнять int: иначе тайм-аут станет отрицательным и goto "
                        + "провалится сразу");
    }
}
