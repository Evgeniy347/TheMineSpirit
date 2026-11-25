package com.theminespirit.neoforge.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphCadence;
import com.theminespirit.core.navigation.NavWorld;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Set;
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

        assertTrue(
                cached != null && cached.reachable(),
                "Путь по ровному полу обязан найтись: " + cached
                        + " Иначе тест cache_SameHelperAndTarget_KeepsVerdictDespiteNewWorld перестанет ловить эту поломку. При смене cache_SameHelperAndTarget_KeepsVerdictDespiteNewWorld проверь это утверждение.");
        assertEquals(
                cached,
                repeated,
                "Повторный вопрос отдаёт тот же вердикт из кэша"
                        + " Иначе тест cache_SameHelperAndTarget_KeepsVerdictDespiteNewWorld перестанет ловить эту поломку. При смене cache_SameHelperAndTarget_KeepsVerdictDespiteNewWorld проверь это утверждение.");
        assertEquals(
                cached,
                cache.verdict(HELPER, FROM, TARGET, 110),
                "Пока (помощник, цель) не изменились и вердикт не истёк, новый мир в том же тике не запускает "
                        + "поиск снова: иначе тик графа снова ждёт до 600 мс на кандидата (debtnav, п.2)"
                        + " При смене cache_SameHelperAndTarget_KeepsVerdictDespiteNewWorld проверь это утверждение.");
    }

    @Test
    void cache_ExpiredVerdict_SearchRunsAgain() {
        RescueReach.Cache cache = new RescueReach.Cache();
        cache.request(FLAT, cell -> false, HELPER, FROM, TARGET, 100);
        finish(cache, 100);

        assertNull(
                cache.verdict(HELPER, FROM, TARGET, 100 + 1200),
                "Просроченный вердикт не выдаётся за свежий: обстановка могла измениться"
                        + " Иначе тест cache_ExpiredVerdict_SearchRunsAgain перестанет ловить эту поломку. При смене cache_ExpiredVerdict_SearchRunsAgain проверь это утверждение.");
        cache.request(new Blocked(), cell -> false, HELPER, FROM, TARGET, 100 + 1200);
        finish(cache, 100 + 1200);

        RescueReach.Verdict fresh = cache.verdict(HELPER, FROM, TARGET, 100 + 1200);
        assertTrue(
                fresh != null && !fresh.reachable(),
                "После срока годности путь считается заново: " + fresh
                        + " Иначе тест cache_ExpiredVerdict_SearchRunsAgain перестанет ловить эту поломку. При смене cache_ExpiredVerdict_SearchRunsAgain проверь это утверждение.");
    }

    @Test
    void cache_ClearedAfterRescue_FindsPathLeaderDug() {
        RescueReach.Cache cache = new RescueReach.Cache();
        cache.request(new Blocked(), cell -> false, HELPER, FROM, TARGET, 100);
        finish(cache, 100);
        RescueReach.Verdict before = cache.verdict(HELPER, FROM, TARGET, 100);
        assertTrue(
                before != null && !before.reachable(),
                "До спасения пути домой нет: " + before
                        + " Иначе тест cache_ClearedAfterRescue_FindsPathLeaderDug перестанет ловить эту поломку. При смене cache_ClearedAfterRescue_FindsPathLeaderDug проверь это утверждение.");
        long generation = cache.generation();

        cache.clear();

        assertNull(
                cache.verdict(HELPER, FROM, TARGET, 100),
                "После завершения спасения старый вердикт не годится: ведущий мог прокопать путь"
                        + " Иначе тест cache_ClearedAfterRescue_FindsPathLeaderDug перестанет ловить эту поломку. При смене cache_ClearedAfterRescue_FindsPathLeaderDug проверь это утверждение.");
        assertTrue(
                cache.generation() > generation,
                "Сброс кэша обязан разбудить граф: ведомые ждут решения, а не следующего цикла до 600 тиков"
                        + " Иначе тест cache_ClearedAfterRescue_FindsPathLeaderDug перестанет ловить эту поломку. При смене cache_ClearedAfterRescue_FindsPathLeaderDug проверь это утверждение.");
        cache.request(FLAT, cell -> false, HELPER, FROM, TARGET, 100);
        finish(cache, 100);

        RescueReach.Verdict after = cache.verdict(HELPER, FROM, TARGET, 100);
        assertTrue(
                after != null && after.reachable(),
                "После сброса путь пересчитывается по новому миру и ведомые идут следом: " + after
                        + " Иначе тест cache_ClearedAfterRescue_FindsPathLeaderDug перестанет ловить эту поломку. При смене cache_ClearedAfterRescue_FindsPathLeaderDug проверь это утверждение.");
    }

    @Test
    void cache_Cleared_DropsPendingSearch() {
        RescueReach.Cache cache = new RescueReach.Cache();
        cache.request(FLAT, cell -> false, HELPER, FROM, TARGET, 0);

        cache.clear();
        finish(cache, 0);

        assertNull(
                cache.verdict(HELPER, FROM, TARGET, 0),
                "Незавершённый поиск после сброса не возвращает старый мир: его результаты уже не нужны"
                        + " Иначе тест cache_Cleared_DropsPendingSearch перестанет ловить эту поломку. При смене cache_Cleared_DropsPendingSearch проверь это утверждение.");
    }

    @Test
    void cache_Advance_CompletesWithoutFullTick() {
        RescueReach.Cache cache = new RescueReach.Cache();
        cache.request(FLAT, cell -> false, HELPER, FROM, TARGET, 0);

        assertNull(
                cache.verdict(HELPER, FROM, TARGET, 0),
                "Сразу после запроса вердикта ещё нет: поиск вынесен из тика графа, а не считается целиком (debtnav, п.2)"
                        + " Иначе тест cache_Advance_CompletesWithoutFullTick перестанет ловить эту поломку. При смене cache_Advance_CompletesWithoutFullTick проверь это утверждение.");
        finish(cache, 0);

        RescueReach.Verdict done = cache.verdict(HELPER, FROM, TARGET, 30);
        assertTrue(
                done != null && done.reachable(),
                "Добор по тикам доводит поиск до вердикта: " + done
                        + " Иначе тест cache_Advance_CompletesWithoutFullTick перестанет ловить эту поломку. При смене cache_Advance_CompletesWithoutFullTick проверь это утверждение.");
    }

    @Test
    void cache_CompletedVerdict_WakesGraphCadenceInSameTick() {
        RescueReach.Cache cache = new RescueReach.Cache();
        GraphCadence cadence = new GraphCadence();
        Set<String> before = Set.of(cache.readyEvent());
        assertTrue(
                cadence.due(100, before),
                "Первый прогон графа идёт сразу: вход ещё не запоминался"
                        + " Иначе тест cache_CompletedVerdict_WakesGraphCadenceInSameTick перестанет ловить эту поломку. При смене cache_CompletedVerdict_WakesGraphCadenceInSameTick проверь это утверждение.");
        cadence.finished(100, before);
        assertFalse(
                cadence.due(110, before),
                "Между прогонами граф спит: вход не менялся, а 600 тиков не прошло"
                        + " Иначе тест cache_CompletedVerdict_WakesGraphCadenceInSameTick перестанет ловить эту поломку. При смене cache_CompletedVerdict_WakesGraphCadenceInSameTick проверь это утверждение.");
        cache.request(FLAT, cell -> false, HELPER, FROM, TARGET, 110);
        assertFalse(
                cadence.due(120, Set.of(cache.readyEvent())),
                "Пока вердикта нет, событие «вердикт готов» не меняется: граф не просыпается зря"
                        + " Иначе тест cache_CompletedVerdict_WakesGraphCadenceInSameTick перестанет ловить эту поломку. При смене cache_CompletedVerdict_WakesGraphCadenceInSameTick проверь это утверждение.");
        finish(cache, 120);

        assertTrue(
                cadence.due(120, Set.of(cache.readyEvent())),
                "Готовый вердикт меняет событие «вердикт готов» (поколение " + cache.generation()
                        + "): граф обязан проснуться в тот же тик и выдать помощника, а не ждать следующего цикла "
                        + "до 600 тиков (debtnav, п.2)"
                        + " Иначе тест cache_CompletedVerdict_WakesGraphCadenceInSameTick перестанет ловить эту поломку. При смене cache_CompletedVerdict_WakesGraphCadenceInSameTick проверь это утверждение.");
    }

    @Test
    void verdict_WaterAtEnd_NotReachable() {
        RescueReach.Cache cache = new RescueReach.Cache();
        cache.request(FLAT, cell -> true, HELPER, FROM, TARGET, 0);
        finish(cache, 0);

        RescueReach.Verdict verdict = cache.verdict(HELPER, FROM, TARGET, 30);
        assertFalse(
                verdict != null && verdict.reachable(),
                "Путь, кончающийся в воде, не считается выходом к помощнику: " + verdict
                        + " Иначе тест verdict_WaterAtEnd_NotReachable перестанет ловить эту поломку. При смене verdict_WaterAtEnd_NotReachable проверь это утверждение.");
    }

    @Test
    void cache_Advance_RotatesPendingSearches() {
        RescueReach.Cache cache = new RescueReach.Cache();
        UUID other = UUID.fromString("66666666-7777-8888-9999-000000000000");
        BlockCoord far = new BlockCoord(5000, 64, 0);
        Flat first = new Flat();
        Flat second = new Flat();
        cache.request(first, cell -> false, HELPER, FROM, far, 0);
        cache.request(second, cell -> false, other, FROM, far, 0);
        first.reset();
        second.reset();

        cache.advance(0, 10_000_000L);

        assertTrue(
                (first.reads > 0) != (second.reads > 0),
                "Один шаг кэша обязан обслужить только один запрос: при делении бюджета между всеми"
                        + " помощниками каждый поиск получает доли тика и не успевает за TTL, а дух остаётся"
                        + " в ловушке Иначе тест cache_Advance_RotatesPendingSearches перестанет ловить"
                        + " живой случай spirit-3 от 25.11. При смене cache_Advance_RotatesPendingSearches"
                        + " проверь RescueReach.Cache.advance и BoardSources.advance.");
        cache.advance(1, 10_000_000L);
        assertTrue(
                first.reads > 0 && second.reads > 0,
                "Следующий тик обязан обслужить второй запрос: иначе первый запрос постоянно получает"
                        + " бюджет, а остальные помощники никогда не получают вердикт Иначе тест"
                        + " cache_Advance_RotatesPendingSearches перестанет ловить эту поломку. При смене"
                        + " cache_Advance_RotatesPendingSearches проверь порядок round-robin.");
    }

    private static void finish(RescueReach.Cache cache, long now) {
        for (int step = 0; step < 20; step++) {
            cache.advance(now, 10_000_000L);
        }
    }

    private static class Flat implements NavWorld {
        private int reads;

        void reset() {
            reads = 0;
        }

        @Override
        public boolean available(int x, int z) {
            return true;
        }

        @Override
        public boolean passable(int x, int y, int z) {
            reads++;
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
