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

        assertEquals(
                1,
                first.slices().size(),
                "За шаг ровно один срез: обход не растягивается на весь участок"
                        + " Иначе тест step_SpreadOverSteps_ReportsCycleDoneOnlyOnLastSlice перестанет ловить эту поломку. При смене step_SpreadOverSteps_ReportsCycleDoneOnlyOnLastSlice проверь это утверждение.");
        assertFalse(
                first.cycleDone(),
                "Первый срез из двух не закрывает цикл: результат ещё неполный"
                        + " Иначе тест step_SpreadOverSteps_ReportsCycleDoneOnlyOnLastSlice перестанет ловить эту поломку. При смене step_SpreadOverSteps_ReportsCycleDoneOnlyOnLastSlice проверь это утверждение.");
        assertEquals(
                1,
                second.slices().size(),
                "Второй шаг добирает последний срез"
                        + " Иначе тест step_SpreadOverSteps_ReportsCycleDoneOnlyOnLastSlice перестанет ловить эту поломку. При смене step_SpreadOverSteps_ReportsCycleDoneOnlyOnLastSlice проверь это утверждение.");
        assertTrue(
                second.cycleDone(),
                "Последний срез закрывает цикл: только теперь результат полный"
                        + " Иначе тест step_SpreadOverSteps_ReportsCycleDoneOnlyOnLastSlice перестанет ловить эту поломку. При смене step_SpreadOverSteps_ReportsCycleDoneOnlyOnLastSlice проверь это утверждение.");
    }

    @Test
    void step_AfterCycle_StartsNewSweep() {
        SectionSweep sweep = new SectionSweep(TWO_SLICES, new BlockCoord(0, 0, 0), 1);
        sweep.step();
        sweep.step();

        SectionSweep.Batch next = sweep.step();

        assertEquals(
                1,
                next.slices().size(),
                "После закрытого цикла обход начинается заново, а не замирает"
                        + " Иначе тест step_AfterCycle_StartsNewSweep перестанет ловить эту поломку. При смене step_AfterCycle_StartsNewSweep проверь это утверждение.");
        assertFalse(
                next.cycleDone(),
                "Новый цикл на первом срезе ещё не полон"
                        + " Иначе тест step_AfterCycle_StartsNewSweep перестанет ловить эту поломку. При смене step_AfterCycle_StartsNewSweep проверь это утверждение.");
    }

    @Test
    void step_PortionLargerThanCycle_ReturnsWholeCycleAtOnce() {
        SectionSweep sweep = new SectionSweep(TWO_SLICES, new BlockCoord(0, 0, 0), 5);

        SectionSweep.Batch batch = sweep.step();

        assertEquals(
                2,
                batch.slices().size(),
                "Порция больше куба отдаёт весь куб, а не пустоту"
                        + " Иначе тест step_PortionLargerThanCycle_ReturnsWholeCycleAtOnce перестанет ловить эту поломку. При смене step_PortionLargerThanCycle_ReturnsWholeCycleAtOnce проверь это утверждение.");
        assertTrue(
                batch.cycleDone(),
                "Весь куб за один шаг закрывает цикл"
                        + " Иначе тест step_PortionLargerThanCycle_ReturnsWholeCycleAtOnce перестанет ловить эту поломку. При смене step_PortionLargerThanCycle_ReturnsWholeCycleAtOnce проверь это утверждение.");
    }
}
