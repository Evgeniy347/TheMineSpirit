package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MiningTuningTests {
    @Test
    void maxApproachFailures_ThreeAttempts_BeforeExclusion() {
        assertEquals(3, MiningTuning.MAX_APPROACH_FAILURES,
                "Стоянка исключается, а цель бросается после трёх неудачных подходов: один-два срыва "
                        + "под нагрузкой — шум навигации, три подряд — недостижимость. Меньше — ложные "
                        + "исключения хороших стоянок, больше — сотни тиков на заведомо глухую цель");
    }
}
