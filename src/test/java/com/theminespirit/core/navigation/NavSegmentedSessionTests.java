// lib: Из Baritone (github.com/cabaletta/baritone, behavior/PathingBehavior.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class NavSegmentedSessionTests {
    private static final long MILLI = 1_000_000L;

    @Test
    void step_SmallSlices_ReturnsNullThenFound() {
        AtomicLong now = new AtomicLong();
        LongSupplier clock = () -> now.addAndGet(MILLI);
        NavSegmentedSession session = new NavSegmentedSession(
                context(NavTestWorld.flat(0), 40),
                new BlockCoord(0, 1, 0),
                new NavGoalBlock(100, 1, 0),
                50,
                6,
                0.5,
                clock);

        NavSegmentedRoute result = null;
        int steps = 0;
        while (result == null && steps < 500) {
            result = session.step(10 * MILLI);
            steps++;
        }

        assertTrue(result != null, "сегменты не теряются между порциями");
        assertEquals(NavSearchStatus.FOUND, result.status(), "цель достигается за несколько порций");
        assertTrue(steps > 1, "одной порции не хватило: поиск разбит по тикам");
        assertTrue(session.done(), "сессия завершена");
    }

    @Test
    void step_MaxSegments_StopsAtLimit() {
        NavSegmentedSession session = new NavSegmentedSession(
                context(NavTestWorld.flat(0), 40), new BlockCoord(0, 1, 0), new NavGoalBlock(100, 1, 0), 1, 6, 0.5);

        NavSegmentedRoute result = session.step(Long.MAX_VALUE);

        assertEquals(NavSearchStatus.SEGMENT, result.status(), "один сегмент не доходит до цели");
        assertEquals(1, result.segments(), "сессия уважает предел сегментов");
    }

    @Test
    void step_AfterDone_ReturnsSameResult() {
        NavSegmentedSession session = new NavSegmentedSession(
                context(NavTestWorld.flat(0), 40), new BlockCoord(0, 1, 0), new NavGoalBlock(100, 1, 0), 50, 6, 0.5);
        NavSegmentedRoute first = session.step(Long.MAX_VALUE);

        NavSegmentedRoute second = session.step(Long.MAX_VALUE);

        assertSame(first, second, "завершённая сессия не пересчитывает путь");
    }

    @Test
    void step_UnloadedWithoutCache_StopsWithoutLoop() {
        NavSegmentedSession session = new NavSegmentedSession(
                context(NavTestWorld.flat(0).unloadFrom(1), 80),
                new BlockCoord(0, 1, 0),
                new NavGoalBlock(128, 1, 0),
                50,
                6,
                0.5);

        NavSegmentedRoute result = session.step(Long.MAX_VALUE);

        assertEquals(NavSearchStatus.SEGMENT, result.status(), "без кэша остаётся сегмент до границы");
        assertTrue(result.end().x() < 16, "поиск не заходит в незагруженный чанк");
        assertTrue(result.segments() <= 2, "после границы нет прогресса — сессия прекращается");
    }

    private static NavContext context(NavWorld world, int maxNodes) {
        return new NavContext(world, new NavSettings(10_000, 10_000, maxNodes, 50, 3, 12, 2, 20, 2, true, true), 64);
    }
}
