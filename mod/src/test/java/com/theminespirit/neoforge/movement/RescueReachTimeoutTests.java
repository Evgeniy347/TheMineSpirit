package com.theminespirit.neoforge.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.navigation.NavWorld;
import com.theminespirit.core.observation.BlockCoord;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RescueReachTimeoutTests {
    private static final UUID HELPER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final BlockCoord FROM = new BlockCoord(0, 64, 0);
    private static final BlockCoord TARGET = new BlockCoord(60, 64, 0);
    private static final NavWorld FLAT = new Flat();

    @Test
    void cache_SearchTimeout_DoesNotBecomeNegativeVerdict() {
        RescueReach.Cache cache = RescueReach.cache();
        cache.request(FLAT, cell -> false, HELPER, FROM, TARGET, 0);
        assertEquals(
                200,
                cache.pendingTicks(HELPER, FROM, TARGET, 200),
                "Возраст незавершённого поиска доступен выдаче: иначе временная выдача не сможет "
                        + "отличить медленный путь от зависшего. При смене cache_SearchTimeout_DoesNotBecomeNegativeVerdict "
                        + "проверь RescueReach.Cache.pendingTicks.");

        cache.advance(1200, 1L);

        assertNull(
                cache.verdict(HELPER, FROM, TARGET, 1200),
                "Исчерпание бюджета не означает отсутствие пути: иначе все дальние задания получают "
                        + "ложный BLOCKED на 1200 тиков. При смене cache_SearchTimeout_DoesNotBecomeNegativeVerdict "
                        + "проверь RescueReach.Cache.advance и живой (-163,67,-522).");
        cache.request(FLAT, cell -> false, HELPER, FROM, TARGET, 1200);
        for (int step = 0; step < 30; step++) {
            cache.advance(1200, 10_000_000L);
        }

        RescueReach.Verdict retried = cache.verdict(HELPER, FROM, TARGET, 1200);
        assertTrue(
                retried != null && retried.reachable(),
                "После таймаута поиск запускается заново: иначе один медленный проход навсегда закроет "
                        + "достижимую цель. При смене cache_SearchTimeout_DoesNotBecomeNegativeVerdict проверь "
                        + "удаление pending без remember.");
    }

    @Test
    void cache_PendingQueueFull_StillAgesDeferredRequest() {
        RescueReach.Cache cache = RescueReach.cache();
        for (int index = 0; index < 16; index++) {
            cache.request(FLAT, cell -> false, HELPER, FROM, new BlockCoord(index + 1, 64, 0), 0);
        }
        BlockCoord deferredTarget = new BlockCoord(100, 64, 0);
        cache.request(FLAT, cell -> false, HELPER, FROM, deferredTarget, 0);

        assertEquals(
                200,
                cache.pendingTicks(HELPER, FROM, deferredTarget, 200),
                "Запрос сверх лимита тоже получает возраст: иначе 16 поисков навсегда оставят "
                        + "остальных духов без заданий. При смене cache_PendingQueueFull_StillAgesDeferredRequest "
                        + "проверь RescueReach.Cache.deferred и NavigationReach.");
    }

    private static final class Flat implements NavWorld {
        @Override
        public boolean available(int x, int z) {
            return true;
        }

        @Override
        public boolean passable(int x, int y, int z) {
            return y >= 64;
        }

        @Override
        public boolean walkable(int x, int y, int z) {
            return y == 63;
        }

        @Override
        public boolean water(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean climbable(int x, int y, int z) {
            return false;
        }

        @Override
        public double breakTicks(int x, int y, int z) {
            return 7;
        }

        @Override
        public int minY() {
            return -64;
        }

        @Override
        public int maxY() {
            return 320;
        }
    }
}
