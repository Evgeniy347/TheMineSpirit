package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class NavReversibilityCacheTests {
    private static final NavSettings LIVE = new NavSettings(600_000, 600_000, 2_000, 50, 3, 12, 2, 20, 2, false, false);

    @Test
    void livePit_DeepSearch_ReturnableCacheStaysBounded() throws IOException {
        NavAStarSession session = new NavAStarSession(
                new NavContext(LiveNavWorld.live(), LIVE, 0), 0, 0, 0, new NavGoalBlock(0, 14, 0), NavFavoring.EMPTY);
        NavResult result = null;
        for (int step = 0; step < 50 && result == null; step++) {
            result = session.step(5_000_000L);
        }

        assertTrue(
                session.returnableCacheSize() > 0,
                "Живая яма склада (-180,57,-519) обязана наполнять кэш обратимости: иначе проверять нечего");
        assertTrue(
                session.returnableCacheSize() <= NavAStarSession.MAX_RETURNABLE_CACHE,
                "Кэш обратимости на живой яме вырос до " + session.returnableCacheSize() + " клеток при пределе "
                        + NavAStarSession.MAX_RETURNABLE_CACHE + ": сессия поиска съедает память под каждый спуск "
                        + "(debtnav, п.4)");
    }

    @Test
    void wideBasin_ManyBelowReference_CacheIsClearedAtLimit() {
        NavTestWorld world = NavTestWorld.flat(-1).solid(1, 0, 0);
        NavSettings basin = new NavSettings(600_000, 600_000, 2_000, 50, 3, 12, 2, 20, 2, false, false);
        NavAStarSession session = new NavAStarSession(
                new NavContext(world, basin, 0), 0, 0, 0, new NavGoalBlock(40, 1, 0), NavFavoring.EMPTY);
        NavResult result = null;
        for (int step = 0; step < 100 && result == null; step++) {
            result = session.step(20_000_000L);
        }

        assertTrue(
                session.returnableCacheSize() <= NavAStarSession.MAX_RETURNABLE_CACHE,
                "Кэш обратимости вырос до " + session.returnableCacheSize() + " клеток при пределе "
                        + NavAStarSession.MAX_RETURNABLE_CACHE + ": BFS на каждую клетку спуска съедает память "
                        + "(debtnav, п.4)");
        assertTrue(
                session.returnableCacheClears() > 0,
                "Предел кэша ни разу не сработал на широкой низине со ступенью (размер "
                        + session.returnableCacheSize() + ", сбросов " + session.returnableCacheClears()
                        + ") — сторож бесполезен");
    }
}
