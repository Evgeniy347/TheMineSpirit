package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class NavChunkLoadingTests {
    private static final int MIN_Y = -16;
    private static final int HEIGHT = 32;

    @Test
    void route_GoalInUnloadedChunk_NotDeclaredAirThenFoundWhenLoaded() {
        NavSegmentedRoute before = plan(NavTestWorld.flat(0).unloadFrom(5));

        assertNotEquals(
                NavSearchStatus.FOUND,
                before.status(),
                "observe22: цель в незагруженном чанке — «неизвестно», а не воздух: расчёт не объявляет путь "
                        + "найденным, пока чанк не подгружен");

        NavSegmentedRoute after = plan(NavTestWorld.flat(0));

        assertEquals(
                NavSearchStatus.FOUND,
                after.status(),
                "Чанк коридора подгружен (NavigationTickets) — путь находится: дух не ходит по кругу "
                        + "«маршрут не найден»");
    }

    @Test
    void route_CachedUnloadedChunks_SegmentedPathCrossesBorder() {
        NavTestWorld full = NavTestWorld.flat(0);
        NavCachedWorld cache = new NavCachedWorld();
        for (int chunkX = 0; chunkX <= 8; chunkX++) {
            cache.put(NavCachedChunk.pack(chunkX, 0, MIN_Y, HEIGHT, source(full)));
        }
        NavSegmentedRoute route = plan(new NavCacheWorld(NavTestWorld.flat(0).unloadFrom(1), cache), 80, 128);

        assertEquals(
                NavSearchStatus.FOUND,
                route.status(),
                "Сегментированный путь обязан идти по кэшу незагруженных чанков: без этого дух упирается в границу "
                        + "загруженного и бросает дальнюю цель");
        assertEquals(128, route.end().x(), "Кэш довёл сегменты до цели за границей живого мира: " + route.end());
    }

    private static NavSegmentedRoute plan(NavWorld world) {
        return plan(world, 200_000, 100);
    }

    private static NavSegmentedRoute plan(NavWorld world, int maxNodes, int goalX) {
        NavSegmentedSession session = new NavSegmentedSession(
                new NavContext(world, new NavSettings(10_000, 10_000, maxNodes, 50, 3, 12, 2, 20, 2, true, true), 64),
                new BlockCoord(0, 1, 0),
                new NavGoalBlock(goalX, 1, 0),
                50,
                6,
                0.5);
        return session.step(Long.MAX_VALUE);
    }

    private static NavChunkSource source(NavTestWorld world) {
        return (x, y, z) -> {
            if (world.water(x, y, z)) {
                return NavBlockType.WATER;
            }
            if (world.walkable(x, y, z)) {
                return NavBlockType.SOLID;
            }
            return world.passable(x, y, z) ? NavBlockType.AIR : NavBlockType.AVOID;
        };
    }
}
