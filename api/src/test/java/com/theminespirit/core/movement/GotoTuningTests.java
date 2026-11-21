package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.movement.api.GotoTuning;
import org.junit.jupiter.api.Test;

class GotoTuningTests {
    @Test
    void stallTicks_ThirtyTicks_TrimsSpinBelowDetector() {
        assertEquals(
                30,
                GotoTuning.STALL_TICKS,
                "Топтание подхода ловится через 30 тиков, а не 40: эпизод кружения требует 40 тиков "
                        + "(окно 59, spirit-1/2: 284° и 574° за 40–42 тика при сдвиге 0.34–0.44, слой "
                        + "ванильный). Иначе отказ приходит ровно на пороге и детектор успевает записать "
                        + "эпизод. При смене окна проверь StuckWatcher: у него остаётся 40 тиков");
    }

    @Test
    void timeoutTicks_KnownDistance_AddsReserveToTicksPerBlock() {
        assertEquals(
                280,
                GotoTuning.timeoutTicks(2),
                "Тайм-аут обязан быть 40*2+200 = 280 тиков: фиксированные 300 тиков отсекали бы дальнюю цель "
                        + "раньше, чем дух успеет дойти"
                        + " Иначе тест timeoutTicks_KnownDistance_AddsReserveToTicksPerBlock перестанет ловить эту поломку. При смене timeoutTicks_KnownDistance_AddsReserveToTicksPerBlock проверь это утверждение.");
    }

    @Test
    void timeoutTicks_DistanceIncreasesTimeout_Linearly() {
        assertEquals(
                600,
                GotoTuning.timeoutTicks(10),
                "Тайм-аут обязан расти с дистанцией: 40*10+200 = 600 тиков, иначе дальний goto сдаётся на полпути"
                        + " При смене timeoutTicks_DistanceIncreasesTimeout_Linearly проверь это утверждение.");
    }

    @Test
    void timeoutTicks_ZeroDistance_ReturnsReserve() {
        assertEquals(
                200,
                GotoTuning.timeoutTicks(0),
                "Нулевая дистанция оставляет только запас: цель рядом, но дух должен успеть дойти"
                        + " Иначе тест timeoutTicks_ZeroDistance_ReturnsReserve перестанет ловить эту поломку. При смене timeoutTicks_ZeroDistance_ReturnsReserve проверь это утверждение.");
    }

    @Test
    void timeoutTicks_HugeDistance_ClampsToIntegerMax() {
        assertEquals(
                Integer.MAX_VALUE,
                GotoTuning.timeoutTicks(100_000_000),
                "Огромная дистанция не должна переполнять int: иначе тайм-аут станет отрицательным и goto "
                        + "провалится сразу"
                        + " При смене timeoutTicks_HugeDistance_ClampsToIntegerMax проверь это утверждение.");
    }

    @Test
    void earlyRefusalRange_EightBlocks_PartialPathIsFinal() {
        assertEquals(
                8,
                GotoTuning.EARLY_REFUSAL_RANGE,
                "Отказ по тупику действует в пределах 8 блоков от цели: дальше частичный путь — норма "
                        + "ограничения поиска, а судить его как глухую цель нельзя"
                        + " Иначе тест earlyRefusalRange_EightBlocks_PartialPathIsFinal перестанет ловить эту поломку. При смене earlyRefusalRange_EightBlocks_PartialPathIsFinal проверь это утверждение.");
    }

    @Test
    void deadEndConfirmTicks_ThreeTicks_ConfirmsStalePath() {
        assertEquals(
                3,
                GotoTuning.DEAD_END_CONFIRM_TICKS,
                "Тупик подтверждается три тика подряд: одиночный кадр с готовым путём бывает и на проходе "
                        + "при перепрокладке, а сорок тиков застревания — слишком долго для глухой стоянки"
                        + " Иначе тест deadEndConfirmTicks_ThreeTicks_ConfirmsStalePath перестанет ловить эту поломку. При смене deadEndConfirmTicks_ThreeTicks_ConfirmsStalePath проверь это утверждение.");
    }
}
