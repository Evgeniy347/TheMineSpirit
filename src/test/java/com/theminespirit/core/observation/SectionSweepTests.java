package com.theminespirit.core.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SectionSweepTests {
    private static final BlockBox TWO_SLICES = new BlockBox(new BlockCoord(0, 0, 0), new BlockCoord(31, 15, 15));

    @Test
    void step_SpreadOverSteps_ReportsCycleDoneOnlyOnLastSlice() {
        SectionSweep sweep = new SectionSweep(TWO_SLICES, new BlockCoord(0, 0, 0), 1);

        SectionSweep.Batch first = sweep.step();
        SectionSweep.Batch second = sweep.step();

        assertEquals(1, first.slices().size(), "За шаг ровно один срез: обход не растягивается на весь участок");
        assertFalse(first.cycleDone(), "Первый срез из двух не закрывает цикл: результат ещё неполный");
        assertEquals(1, second.slices().size(), "Второй шаг добирает последний срез");
        assertTrue(second.cycleDone(), "Последний срез закрывает цикл: только теперь результат полный");
    }

    @Test
    void step_AfterCycle_StartsNewSweep() {
        SectionSweep sweep = new SectionSweep(TWO_SLICES, new BlockCoord(0, 0, 0), 1);
        sweep.step();
        sweep.step();

        SectionSweep.Batch next = sweep.step();

        assertEquals(1, next.slices().size(), "После закрытого цикла обход начинается заново, а не замирает");
        assertFalse(next.cycleDone(), "Новый цикл на первом срезе ещё не полон");
    }

    @Test
    void step_PortionLargerThanCycle_ReturnsWholeCycleAtOnce() {
        SectionSweep sweep = new SectionSweep(TWO_SLICES, new BlockCoord(0, 0, 0), 5);

        SectionSweep.Batch batch = sweep.step();

        assertEquals(2, batch.slices().size(), "Порция больше куба отдаёт весь куб, а не пустоту");
        assertTrue(batch.cycleDone(), "Весь куб за один шаг закрывает цикл");
    }
}
