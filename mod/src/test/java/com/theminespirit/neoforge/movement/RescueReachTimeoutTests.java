package com.theminespirit.neoforge.movement;

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
    void cache_SearchAcrossCadence_KeepsProbeUntilVerdict() {
        RescueReach.Cache cache = RescueReach.cache();
        cache.request(FLAT, cell -> false, HELPER, FROM, TARGET, 0);
        cache.advance(1200, 1L);
        for (int step = 0; step < 30; step++) {
            cache.advance(1200, 10_000_000L);
        }

        RescueReach.Verdict completed = cache.verdict(HELPER, FROM, TARGET, 1200);
        assertTrue(
                completed != null && completed.reachable(),
                "Медленный поиск продолжается между циклами графа и завершается вердиктом: иначе cadence "
                        + "сбрасывает pending каждый раз и rescue никогда не получает помощника. При смене "
                        + "cache_SearchAcrossCadence_KeepsProbeUntilVerdict проверь RescueReach.Cache.advance.");
    }

    @Test
    void cache_PendingQueueFull_DoesNotLoseDeferredRequest() {
        RescueReach.Cache cache = RescueReach.cache();
        for (int index = 0; index < 64; index++) {
            cache.request(FLAT, cell -> false, HELPER, FROM, new BlockCoord(index + 1, 64, 0), 0);
        }
        BlockCoord deferredTarget = new BlockCoord(100, 64, 0);
        cache.request(FLAT, cell -> false, HELPER, FROM, deferredTarget, 0);

        for (int step = 0; step < 30; step++) {
            cache.advance(200, 10_000_000L);
        }
        cache.request(FLAT, cell -> false, HELPER, FROM, deferredTarget, 200);
        for (int step = 0; step < 100; step++) {
            cache.advance(200, 10_000_000L);
        }

        RescueReach.Verdict completed = cache.verdict(HELPER, FROM, deferredTarget, 200);
        assertTrue(
                completed != null && completed.reachable(),
                "Отложенный запрос не теряется после освобождения очереди: иначе помощник навсегда "
                        + "остаётся без пути при 64 pending-поисках. При смене "
                        + "cache_PendingQueueFull_DoesNotLoseDeferredRequest проверь RescueReach.Cache.request.");
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
