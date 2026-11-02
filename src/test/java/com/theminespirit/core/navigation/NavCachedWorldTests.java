// lib: Из Baritone (github.com/cabaletta/baritone, src/main/java/baritone/cache/CachedWorld.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NavCachedWorldTests {
    private static final int MIN_Y = -16;
    private static final int HEIGHT = 32;

    @Test
    void put_ChunksAcrossRegionBorder_AreFoundByBlockCoordinates() {
        NavCachedWorld world = new NavCachedWorld();
        for (int chunkX : new int[] {0, 31, 32, -1}) {
            world.put(NavCachedChunk.pack(
                    chunkX, 0, MIN_Y, HEIGHT, (x, y, z) -> y < 0 ? NavBlockType.SOLID : NavBlockType.AIR));
        }

        assertEquals(4, world.cachedChunks(), "все четыре чанка на месте, включая границу регионов");
        assertTrue(world.isCached(0, 0), "нулевой чанк кэширован");
        assertTrue(world.isCached(31 * 16, 0), "чанк 31 кэширован");
        assertTrue(world.isCached(32 * 16, 0), "чанк 32 — уже второй регион");
        assertTrue(world.isCached(-16, 0), "отрицательный чанк кэширован");
        assertFalse(world.isCached(33 * 16, 0), "некэшированный чанк не выдумывается");
        assertNull(world.type(33 * 16, 1, 0), "у некэшированного блока типа нет");
        assertEquals(NavBlockType.SOLID, world.type(0, -1, 0), "пол читается из кэша");
        assertTrue(world.cachedBytes() > 0, "кэш считает занятую память");
    }
}
