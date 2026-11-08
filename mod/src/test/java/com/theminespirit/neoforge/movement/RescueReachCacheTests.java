package com.theminespirit.neoforge.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.navigation.NavWorld;
import com.theminespirit.core.observation.BlockCoord;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RescueReachCacheTests {
    private static final UUID HELPER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final BlockCoord FROM = new BlockCoord(0, 64, 0);
    private static final BlockCoord TARGET = new BlockCoord(60, 64, 0);
    private static final NavWorld FLAT = new Flat();

    @Test
    void cache_SameHelperAndTarget_KeepsVerdictDespiteNewWorld() {
        RescueReach.Cache cache = RescueReach.cache();
        cache.request(FLAT, cell -> false, HELPER, FROM, TARGET, 100);
        finish(cache, 100);

        RescueReach.Verdict cached = cache.verdict(HELPER, FROM, TARGET, 110);
        cache.request(new Blocked(), cell -> false, HELPER, FROM, TARGET, 110);
        RescueReach.Verdict repeated = cache.verdict(HELPER, FROM, TARGET, 110);

        assertTrue(cached != null && cached.reachable(), "Путь по ровному полу обязан найтись: " + cached);
        assertEquals(cached, repeated, "Повторный вопрос отдаёт тот же вердикт из кэша");
        assertEquals(
                cached,
                cache.verdict(HELPER, FROM, TARGET, 110),
                "Пока (помощник, цель) не изменились и вердикт не истёк, новый мир в том же тике не запускает "
                        + "поиск снова: иначе тик графа снова ждёт до 600 мс на кандидата (debtnav, п.2)");
    }

    @Test
    void cache_ExpiredVerdict_SearchRunsAgain() {
        RescueReach.Cache cache = new RescueReach.Cache();
        cache.request(FLAT, cell -> false, HELPER, FROM, TARGET, 100);
        finish(cache, 100);

        assertNull(
                cache.verdict(HELPER, FROM, TARGET, 100 + 1200),
                "Просроченный вердикт не выдаётся за свежий: обстановка могла измениться");
        cache.request(new Blocked(), cell -> false, HELPER, FROM, TARGET, 100 + 1200);
        finish(cache, 100 + 1200);

        RescueReach.Verdict fresh = cache.verdict(HELPER, FROM, TARGET, 100 + 1200);
        assertTrue(fresh != null && !fresh.reachable(), "После срока годности путь считается заново: " + fresh);
    }

    @Test
    void cache_Advance_CompletesWithoutFullTick() {
        RescueReach.Cache cache = new RescueReach.Cache();
        cache.request(FLAT, cell -> false, HELPER, FROM, TARGET, 0);

        assertNull(
                cache.verdict(HELPER, FROM, TARGET, 0),
                "Сразу после запроса вердикта ещё нет: поиск вынесен из тика графа, а не считается целиком (debtnav, п.2)");
        finish(cache, 0);

        RescueReach.Verdict done = cache.verdict(HELPER, FROM, TARGET, 30);
        assertTrue(done != null && done.reachable(), "Добор по тикам доводит поиск до вердикта: " + done);
    }

    @Test
    void cache_CompletedVerdict_AdvancesGeneration() {
        RescueReach.Cache cache = new RescueReach.Cache();
        long before = cache.generation();
        cache.request(FLAT, cell -> false, HELPER, FROM, TARGET, 0);
        finish(cache, 0);

        assertTrue(
                cache.generation() > before,
                "Готовый вердикт меняет поколение кэша: граф обязан проснуться и выдать помощника, а не ждать "
                        + "следующего цикла до 600 тиков (debtnav, п.2)");
    }

    @Test
    void verdict_WaterAtEnd_NotReachable() {
        RescueReach.Cache cache = new RescueReach.Cache();
        cache.request(FLAT, cell -> true, HELPER, FROM, TARGET, 0);
        finish(cache, 0);

        RescueReach.Verdict verdict = cache.verdict(HELPER, FROM, TARGET, 30);
        assertFalse(
                verdict != null && verdict.reachable(),
                "Путь, кончающийся в воде, не считается выходом к помощнику: " + verdict);
    }

    private static void finish(RescueReach.Cache cache, long now) {
        for (int step = 0; step < 20; step++) {
            cache.advance(now, 10_000_000L);
        }
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

    private static final class Blocked implements NavWorld {
        @Override
        public boolean available(int x, int z) {
            return true;
        }

        @Override
        public boolean passable(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean walkable(int x, int y, int z) {
            return false;
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
