package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MiningTuningTests {
    @Test
    void maxStandApproachFailures_ThreeAttempts_BeforeExclusion() {
        assertEquals(3, MiningTuning.MAX_STAND_APPROACH_FAILURES,
                "Стоянка исключается после трёх неудачных подходов: один-два срыва под нагрузкой — шум "
                        + "навигации, три подряд — недостижимость. Меньше — ложные исключения хороших стоянок, "
                        + "больше — сотни тиков на заведомо глухую цель");
    }
}
