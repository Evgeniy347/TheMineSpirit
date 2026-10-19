package com.theminespirit.core.observation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScanTuningTests {
    private static final int POSITIONS_PER_SECTION = 16 * 16 * 16;
    private static final int READS_PER_TICK_BUDGET = 16_384;

    @Test
    void slicesPerTick_FullSections_StayWithinReadBudgetPerTick() {
        long worstCaseReads = (long) ScanTuning.SLICES_PER_TICK * POSITIONS_PER_SECTION;

        assertTrue(
                worstCaseReads <= READS_PER_TICK_BUDGET,
                "Порция скана из " + ScanTuning.SLICES_PER_TICK + " срезов читает до " + worstCaseReads
                        + " блоков за тик при бюджете " + READS_PER_TICK_BUDGET
                        + ": больший размер порции кладёт серверный тик на радиусе 32; "
                        + "меняя число осознанно, пересмотри бюджет в чеклисте 10");
    }

    @Test
    void slicesPerTick_Radius32Scan_FinishesWithinAFewDozenTicks() {
        int radiusCubeSections = 5 * 5 * 5;
        int ticks = (radiusCubeSections + ScanTuning.SLICES_PER_TICK - 1) / ScanTuning.SLICES_PER_TICK;

        assertTrue(
                ticks <= 40,
                "Скан радиуса 32 (до 125 срезов) обязан укладываться в ~32 тика, а получилось " + ticks
                        + ": слишком малая порция делает ответ scan неприемлемо медленным для планировщика");
    }
}
