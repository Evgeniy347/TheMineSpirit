package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApproachFailureTrackerTests {
    private static final BlockCoord STAND = new BlockCoord(4, 1, 4);
    private static final BlockCoord TARGET = new BlockCoord(4, 1, 3);

    @Test
    void recordFailure_BelowLimit_RetriesTarget() {
        ApproachFailureTracker tracker = new ApproachFailureTracker();

        for (int attempt = 1; attempt < MiningTuning.MAX_APPROACH_FAILURES; attempt++) {
            assertFalse(
                    tracker.recordFailure(STAND, TARGET),
                    "Попытка " + attempt + " из " + MiningTuning.MAX_APPROACH_FAILURES
                            + " обязана просить повтор: один-два срыва под нагрузкой — шум навигации, "
                            + "а не недостижимость. ИначеTransient-сбои будут терять достижимые цели");
        }
    }

    @Test
    void recordFailure_AtLimit_DropsTarget() {
        ApproachFailureTracker tracker = new ApproachFailureTracker();
        boolean dropped = false;

        for (int attempt = 1; attempt <= MiningTuning.MAX_APPROACH_FAILURES; attempt++) {
            dropped = tracker.recordFailure(STAND, TARGET);
        }

        assertTrue(
                dropped,
                "После " + MiningTuning.MAX_APPROACH_FAILURES
                        + " неудачных подходов цель обязана бросаться: иначе дух сотнями тиков идёт "
                        + "в заведомо глухое место");
    }

    @Test
    void recordFailure_AtLimit_ExcludesStand() {
        ApproachFailureTracker tracker = new ApproachFailureTracker();

        for (int attempt = 1; attempt <= MiningTuning.MAX_APPROACH_FAILURES; attempt++) {
            tracker.recordFailure(STAND, TARGET);
        }

        assertEquals(
                Set.of(STAND),
                tracker.excludedStands(),
                "Стоянка с " + MiningTuning.MAX_APPROACH_FAILURES
                        + " неудачными подходами обязана исключаться: планировщик иначе снова выберет "
                        + "то же глухое место для следующей цели");
    }

    @Test
    void recordFailure_FailuresOnDifferentStands_KeepsStandsIncluded() {
        ApproachFailureTracker tracker = new ApproachFailureTracker();
        BlockCoord other = new BlockCoord(0, 1, 0);

        tracker.recordFailure(STAND, TARGET);
        tracker.recordFailure(other, TARGET);

        assertTrue(
                tracker.excludedStands().isEmpty(),
                "По одному срыву на разных стоянках — шум, а не глухое место: исключение обязано требовать "
                        + MiningTuning.MAX_APPROACH_FAILURES
                        + " неудач на одной стоянке, иначе потеряются хорошие места");
    }

    @Test
    void recordFailure_SharedStandExcluded_DropsOtherTargetsFaster() {
        ApproachFailureTracker tracker = new ApproachFailureTracker();
        BlockCoord neighbour = new BlockCoord(5, 1, 4);

        for (int attempt = 1; attempt <= MiningTuning.MAX_APPROACH_FAILURES; attempt++) {
            tracker.recordFailure(STAND, TARGET);
        }

        assertTrue(
                tracker.excludedStands().contains(STAND),
                "Общая глухая стоянка обязана остаться исключённой и для соседней цели: иначе каждая цель "
                        + "потратит полный предел попыток на то же место");
        assertFalse(
                tracker.recordFailure(new BlockCoord(6, 1, 4), neighbour),
                "Соседняя цель с первой попытки обязана просить повтор: её собственный счётчик независим "
                        + "от чужой брошенной цели");
    }

    @Test
    void recordFailure_NullStand_Throws() {
        ApproachFailureTracker tracker = new ApproachFailureTracker();

        assertThrows(
                NullPointerException.class,
                () -> tracker.recordFailure(null, TARGET),
                "Null-стоянка обязана ронять вызов сразу: молчаливое ведение счёта без места исключит не то");
    }

    @Test
    void recordFailure_NullTarget_Throws() {
        ApproachFailureTracker tracker = new ApproachFailureTracker();

        assertThrows(
                NullPointerException.class,
                () -> tracker.recordFailure(STAND, null),
                "Null-цель обязана ронять вызов сразу: молчаливый счёт попыток в никуда скроет зацикливание");
    }
}
