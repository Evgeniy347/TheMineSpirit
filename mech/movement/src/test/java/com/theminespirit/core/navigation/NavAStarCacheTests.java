// lib: Из Baritone (github.com/cabaletta/baritone, pathing/calc/AStarPathFinder.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NavAStarCacheTests {
    private static final int MIN_Y = -16;
    private static final int HEIGHT = 32;

    @Test
    void unloadedChunks_Cached_AllowPathBeyondLoadedBorder() {
        NavTestWorld full = NavTestWorld.flat(0);
        NavCachedWorld cache = new NavCachedWorld();
        for (int chunkX = 0; chunkX <= 8; chunkX++) {
            cache.put(NavCachedChunk.pack(chunkX, 0, MIN_Y, HEIGHT, source(full)));
        }
        NavTestWorld live = NavTestWorld.flat(0).unloadFrom(1);
        NavContext plain = context(live);
        NavContext cached = context(new NavCacheWorld(live, cache));

        NavResult without = plan(plain, 128);
        NavResult with = plan(cached, 128);

        assertEquals(
                NavSearchStatus.SEGMENT,
                without.status(),
                "без кэша поиск упирается в границу чанка"
                        + " Иначе тест unloadedChunks_Cached_AllowPathBeyondLoadedBorder перестанет ловить эту поломку. При смене unloadedChunks_Cached_AllowPathBeyondLoadedBorder проверь это утверждение.");
        assertTrue(
                without.end().x() < 16,
                "без кэша путь не выходит из загруженного чанка: " + without.end()
                        + " Иначе тест unloadedChunks_Cached_AllowPathBeyondLoadedBorder перестанет ловить эту поломку. При смене unloadedChunks_Cached_AllowPathBeyondLoadedBorder проверь это утверждение.");
        assertEquals(
                NavSearchStatus.FOUND,
                with.status(),
                "с кэшем путь идёт по незагруженным чанкам"
                        + " Иначе тест unloadedChunks_Cached_AllowPathBeyondLoadedBorder перестанет ловить эту поломку. При смене unloadedChunks_Cached_AllowPathBeyondLoadedBorder проверь это утверждение.");
        assertEquals(
                128,
                with.end().x(),
                "цель достигнута"
                        + " Иначе тест unloadedChunks_Cached_AllowPathBeyondLoadedBorder перестанет ловить эту поломку. При смене unloadedChunks_Cached_AllowPathBeyondLoadedBorder проверь это утверждение.");
    }

    @Test
    void cachedChunkOne_OnlyOneChunk_AllowsPathToItsFarEdge() {
        NavTestWorld full = NavTestWorld.flat(0);
        NavCachedWorld cache = new NavCachedWorld();
        cache.put(NavCachedChunk.pack(0, 0, MIN_Y, HEIGHT, source(full)));
        cache.put(NavCachedChunk.pack(1, 0, MIN_Y, HEIGHT, source(full)));
        NavTestWorld live = NavTestWorld.flat(0).unloadFrom(1);

        NavResult result = plan(context(new NavCacheWorld(live, cache)), 20);

        assertEquals(
                NavSearchStatus.FOUND,
                result.status(),
                "один кэшированный чанк открывает путь до x=20"
                        + " Иначе тест cachedChunkOne_OnlyOneChunk_AllowsPathToItsFarEdge перестанет ловить эту поломку. При смене cachedChunkOne_OnlyOneChunk_AllowsPathToItsFarEdge проверь это утверждение.");
        assertEquals(
                20,
                result.end().x(),
                "цель внутри кэшированного чанка"
                        + " Иначе тест cachedChunkOne_OnlyOneChunk_AllowsPathToItsFarEdge перестанет ловить эту поломку. При смене cachedChunkOne_OnlyOneChunk_AllowsPathToItsFarEdge проверь это утверждение.");
    }

    private static NavResult plan(NavContext context, int targetX) {
        return NavAStarTestRun.plan(context, 0, 1, 0, new NavGoalBlock(targetX, 1, 0));
    }

    private static NavContext context(NavWorld world) {
        NavSettings settings = new NavSettings(10_000, 10_000, 200_000, 50, 3, 12, 2, 20, 2, true, true);
        return new NavContext(world, settings, 64);
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
