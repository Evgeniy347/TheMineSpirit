package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GotoTuningTests {
    @Test
    void timeoutTicks_KnownDistance_AddsReserveToTicksPerBlock() {
        assertEquals(
                280,
                GotoTuning.timeoutTicks(2),
                "Тайм-аут обязан быть 40*2+200 = 280 тиков: фиксированные 300 тиков отсекали бы дальнюю цель "
                        + "раньше, чем дух успеет дойти");
    }

    @Test
    void timeoutTicks_DistanceIncreasesTimeout_Linearly() {
        assertEquals(
                600,
                GotoTuning.timeoutTicks(10),
                "Тайм-аут обязан расти с дистанцией: 40*10+200 = 600 тиков, иначе дальний goto сдаётся на полпути");
    }

    @Test
    void timeoutTicks_ZeroDistance_ReturnsReserve() {
        assertEquals(
                200,
                GotoTuning.timeoutTicks(0),
                "Нулевая дистанция оставляет только запас: цель рядом, но дух должен успеть дойти");
    }

    @Test
    void timeoutTicks_HugeDistance_ClampsToIntegerMax() {
        assertEquals(
                Integer.MAX_VALUE,
                GotoTuning.timeoutTicks(100_000_000),
                "Огромная дистанция не должна переполнять int: иначе тайм-аут станет отрицательным и goto "
                        + "провалится сразу");
    }

    @Test
    void earlyRefusalRange_EightBlocks_PartialPathIsFinal() {
        assertEquals(
                8,
                GotoTuning.EARLY_REFUSAL_RANGE,
                "Отказ по тупику действует в пределах 8 блоков от цели: дальше частичный путь — норма "
                        + "ограничения поиска, а судить его как глухую цель нельзя");
    }

    @Test
    void deadEndConfirmTicks_ThreeTicks_ConfirmsStalePath() {
        assertEquals(
                3,
                GotoTuning.DEAD_END_CONFIRM_TICKS,
                "Тупик подтверждается три тика подряд: одиночный кадр с готовым путём бывает и на проходе "
                        + "при перепрокладке, а сорок тиков застревания — слишком долго для глухой стоянки");
    }
}
