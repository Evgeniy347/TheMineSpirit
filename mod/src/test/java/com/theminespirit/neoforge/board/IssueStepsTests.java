package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

class IssueStepsTests {
    @Test
    void lap_Step_ReturnsNextStart() {
        IssueSteps steps = new IssueSteps();
        long before = System.nanoTime();
        assertTrue(steps.lap("claimable", before) >= before, "отсечка шага начинает следующий шаг, а не прошлый");
    }

    @Test
    void end_FastIssue_KeptBelowSlowMark() {
        IssueSteps steps = new IssueSteps();
        steps.begin();
        steps.add("choose", System.nanoTime());
        steps.end(0, NOPLogger.NOP_LOGGER);
        assertTrue(
                steps.worstTotal() < IssueSteps.SLOW_MILLIS,
                "быстрая раздача не попадает в сводку медленных (сервер 12.11: dispatch 600–800 мс вне пересборки)");
    }
}
