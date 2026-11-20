// lib: Из Baritone (github.com/cabaletta/baritone, pathing/calc/AStarPathFinder.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class NavAStarSessionTests {
    private static final long MILLI = 1_000_000L;

    @Test
    void step_SmallSlices_ReturnsNullUntilPathFound() {
        AtomicLong now = new AtomicLong();
        LongSupplier clock = () -> now.addAndGet(MILLI);
        NavAStarSession session = new NavAStarSession(
                context(100_000, 10_000), 0, 1, 0, new NavGoalBlock(20, 1, 0), NavFavoring.EMPTY, clock);

        NavResult result = null;
        int steps = 0;
        while (result == null && steps < 500) {
            result = session.step(10 * MILLI);
            steps++;
        }

        assertTrue(
                result != null,
                "маленькие порции не теряют поиск: он продолжается между вызовами"
                        + " Иначе тест step_SmallSlices_ReturnsNullUntilPathFound перестанет ловить эту поломку. При смене step_SmallSlices_ReturnsNullUntilPathFound проверь это утверждение.");
        assertEquals(
                NavSearchStatus.FOUND,
                result.status(),
                "поиск доходит до цели за несколько порций"
                        + " Иначе тест step_SmallSlices_ReturnsNullUntilPathFound перестанет ловить эту поломку. При смене step_SmallSlices_ReturnsNullUntilPathFound проверь это утверждение.");
        assertTrue(
                steps > 1,
                "одной порции в 10 мс не хватило: поиск действительно разбит по тикам"
                        + " Иначе тест step_SmallSlices_ReturnsNullUntilPathFound перестанет ловить эту поломку. При смене step_SmallSlices_ReturnsNullUntilPathFound проверь это утверждение.");
        assertTrue(
                session.terminated(),
                "после результата поиск завершён"
                        + " Иначе тест step_SmallSlices_ReturnsNullUntilPathFound перестанет ловить эту поломку. При смене step_SmallSlices_ReturnsNullUntilPathFound проверь это утверждение.");
    }

    @Test
    void step_MaxNodes_ReturnsSegmentAndTerminates() {
        NavAStarSession session =
                new NavAStarSession(context(10, 10_000), 0, 1, 0, new NavGoalBlock(500, 1, 0), NavFavoring.EMPTY);

        NavResult result = session.step(Long.MAX_VALUE);

        assertEquals(
                NavSearchStatus.SEGMENT,
                result.status(),
                "при пределе узлов отдаётся лучший сегмент"
                        + " Иначе тест step_MaxNodes_ReturnsSegmentAndTerminates перестанет ловить эту поломку. При смене step_MaxNodes_ReturnsSegmentAndTerminates проверь это утверждение.");
        assertTrue(
                session.terminated(),
                "поиск завершён"
                        + " Иначе тест step_MaxNodes_ReturnsSegmentAndTerminates перестанет ловить эту поломку. При смене step_MaxNodes_ReturnsSegmentAndTerminates проверь это утверждение.");
        assertEquals(
                10,
                session.considered(),
                "рассмотрено ровно столько узлов, сколько разрешено"
                        + " Иначе тест step_MaxNodes_ReturnsSegmentAndTerminates перестанет ловить эту поломку. При смене step_MaxNodes_ReturnsSegmentAndTerminates проверь это утверждение.");
    }

    @Test
    void step_ZeroTimeout_ReturnsWithoutHanging() {
        NavAStarSession session =
                new NavAStarSession(context(100_000, 0), 0, 1, 0, new NavGoalBlock(500, 1, 0), NavFavoring.EMPTY);

        NavResult result = session.step(Long.MAX_VALUE);

        assertTrue(
                session.terminated(),
                "нулевой срок сразу завершает поиск"
                        + " Иначе тест step_ZeroTimeout_ReturnsWithoutHanging перестанет ловить эту поломку. При смене step_ZeroTimeout_ReturnsWithoutHanging проверь это утверждение.");
        assertTrue(
                result.status() != NavSearchStatus.FOUND,
                "за нулевой срок цель не находится"
                        + " Иначе тест step_ZeroTimeout_ReturnsWithoutHanging перестанет ловить эту поломку. При смене step_ZeroTimeout_ReturnsWithoutHanging проверь это утверждение.");
    }

    @Test
    void step_AfterTermination_IsRejected() {
        NavAStarSession session =
                new NavAStarSession(context(10, 10_000), 0, 1, 0, new NavGoalBlock(500, 1, 0), NavFavoring.EMPTY);
        session.step(Long.MAX_VALUE);

        assertThrows(
                IllegalStateException.class,
                () -> session.step(MILLI),
                "повторный шаг завершённого поиска — ошибка, а не тихий результат"
                        + " Иначе тест step_AfterTermination_IsRejected перестанет ловить эту поломку. При смене step_AfterTermination_IsRejected проверь это утверждение.");
    }

    @Test
    void step_EmptySlice_ReturnsNullWithoutProgress() {
        NavAStarSession session =
                new NavAStarSession(context(100_000, 10_000), 0, 1, 0, new NavGoalBlock(20, 1, 0), NavFavoring.EMPTY);

        assertNull(
                session.step(0),
                "нулевая порция не делает шагов и просит продолжить позже"
                        + " Иначе тест step_EmptySlice_ReturnsNullWithoutProgress перестанет ловить эту поломку. При смене step_EmptySlice_ReturnsNullWithoutProgress проверь это утверждение.");
    }

    private static NavContext context(int maxNodes, long timeoutMs) {
        return new NavContext(
                NavTestWorld.flat(0),
                new NavSettings(timeoutMs, timeoutMs, maxNodes, 50, 3, 12, 2, 20, 2, true, true),
                64);
    }
}
