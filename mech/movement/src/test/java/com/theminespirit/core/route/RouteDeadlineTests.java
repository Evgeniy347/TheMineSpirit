package com.theminespirit.core.route;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.GoalBlock;
import com.theminespirit.core.observation.BlockCoord;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RouteDeadlineTests {
    private static final RouteRules RULES = RouteRules.load();
    private static final long MILLI = 1_000_000L;

    @Test
    void exhausted_BeforeDeadline_IsFalse() {
        AtomicLong now = new AtomicLong();
        RouteDeadline deadline = RouteDeadline.afterMillis(10, now::get);

        now.set(9 * MILLI);
        assertFalse(deadline.exhausted(), "до срока поиск продолжается: бюджет не срабатывает раньше времени");
    }

    @Test
    void exhausted_AtAndAfterDeadline_IsTrue() {
        AtomicLong now = new AtomicLong();
        RouteDeadline deadline = RouteDeadline.afterMillis(10, now::get);

        now.set(10 * MILLI);
        assertTrue(deadline.exhausted(), "на сроке поиск обязан остановиться");
        now.set(11 * MILLI);
        assertTrue(deadline.exhausted(), "после срока поиск тоже не продолжается");
    }

    @Test
    void afterMillis_Zero_IsExhaustedImmediately() {
        AtomicLong now = new AtomicLong(123);
        assertTrue(
                RouteDeadline.afterMillis(0, now::get).exhausted(),
                "нулевой срок — это «не искать»: нулевой бюджет не должен давать полный перебор");
    }

    @Test
    void afterMillis_Negative_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RouteDeadline.afterMillis(-1, System::nanoTime),
                "отрицательный срок запрещал бы любой поиск и маскировал ошибку настройки");
    }

    @Test
    void afterMillis_LongMax_DoesNotOverflowIntoExpired() {
        AtomicLong now = new AtomicLong(Long.MAX_VALUE / 2);
        assertFalse(
                RouteDeadline.afterMillis(Long.MAX_VALUE, now::get).exhausted(),
                "огромный срок не должен переполниться в уже истёкший бюджет");
    }

    @Test
    void plan_WorldReadsSpendDeadline_StopsBeforeNodeLimit() {
        GridWorld grid = GridWorld.flat(0);
        AtomicLong now = new AtomicLong();
        AtomicInteger reads = new AtomicInteger();
        RouteWorld counted = (x, y, z) -> {
            reads.incrementAndGet();
            now.addAndGet(MILLI);
            return grid.cell(x, y, z);
        };
        RouteDeadline budget = RouteDeadline.afterMillis(10, now::get);

        Route route = RoutePlanner.plan(
                counted, new BlockCoord(0, 0, 0), new GoalBlock(new BlockCoord(200, 50, 0)), RULES, 0, budget);

        assertTrue(
                route.nodes() < 100,
                "срок в 10 мс и чтение по 1 мс обязаны остановить A* за десятки узлов, а не за 40 000: узлов "
                        + route.nodes());
        assertTrue(reads.get() < 1000, "чтений мира при остановке по сроку мало: " + reads.get());
    }
}
