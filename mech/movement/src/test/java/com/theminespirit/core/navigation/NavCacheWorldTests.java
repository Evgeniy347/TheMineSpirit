// lib: Из Baritone (github.com/cabaletta/baritone, utils/BlockStateInterface.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NavCacheWorldTests {
    private static final int MIN_Y = -16;
    private static final int HEIGHT = 32;

    @Test
    void passable_LoadedChunk_IgnoresStaleCache() {
        NavTestWorld live = NavTestWorld.flat(0).solid(1, 1, 0);
        NavCachedWorld cache = new NavCachedWorld();
        cache.put(NavCachedChunk.pack(0, 0, MIN_Y, HEIGHT, (x, y, z) -> y < 0 ? NavBlockType.SOLID : NavBlockType.AIR));
        NavCacheWorld world = new NavCacheWorld(live, cache);

        assertFalse(
                world.passable(1, 1, 0),
                "загруженный чанк читается из мира, а не из устаревшего кэша"
                        + " Иначе тест passable_LoadedChunk_IgnoresStaleCache перестанет ловить эту поломку. При смене passable_LoadedChunk_IgnoresStaleCache проверь это утверждение.");
        assertTrue(
                world.available(0, 0),
                "загруженный чанк доступен"
                        + " Иначе тест passable_LoadedChunk_IgnoresStaleCache перестанет ловить эту поломку. При смене passable_LoadedChunk_IgnoresStaleCache проверь это утверждение.");
    }

    @Test
    void passable_UnloadedCachedChunk_ReadsCache() {
        NavTestWorld live = NavTestWorld.flat(0).unloadFrom(1);
        NavCachedWorld cache = new NavCachedWorld();
        cache.put(NavCachedChunk.pack(1, 0, MIN_Y, HEIGHT, (x, y, z) -> {
            if (y < 0) {
                return NavBlockType.SOLID;
            }
            if (x == 18 && y == 1 && z == 2) {
                return NavBlockType.WATER;
            }
            return NavBlockType.AIR;
        }));
        NavCacheWorld world = new NavCacheWorld(live, cache);

        assertTrue(
                world.available(16, 0),
                "незагруженный, но кэшированный чанк доступен"
                        + " Иначе тест passable_UnloadedCachedChunk_ReadsCache перестанет ловить эту поломку. При смене passable_UnloadedCachedChunk_ReadsCache проверь это утверждение.");
        assertTrue(
                world.passable(16, 1, 0),
                "воздух из кэша"
                        + " Иначе тест passable_UnloadedCachedChunk_ReadsCache перестанет ловить эту поломку. При смене passable_UnloadedCachedChunk_ReadsCache проверь это утверждение.");
        assertTrue(
                world.walkable(16, -1, 0),
                "пол из кэша"
                        + " Иначе тест passable_UnloadedCachedChunk_ReadsCache перестанет ловить эту поломку. При смене passable_UnloadedCachedChunk_ReadsCache проверь это утверждение.");
        assertTrue(
                world.water(18, 1, 2),
                "вода из кэша"
                        + " Иначе тест passable_UnloadedCachedChunk_ReadsCache перестанет ловить эту поломку. При смене passable_UnloadedCachedChunk_ReadsCache проверь это утверждение.");
        assertFalse(
                world.climbable(16, 1, 0),
                "кэш не хранит лестницы — считаем недоступными"
                        + " Иначе тест passable_UnloadedCachedChunk_ReadsCache перестанет ловить эту поломку. При смене passable_UnloadedCachedChunk_ReadsCache проверь это утверждение.");
        assertEquals(
                0,
                world.breakTicks(16, 1, 0),
                0,
                "воздух не требует ломки"
                        + " Иначе тест passable_UnloadedCachedChunk_ReadsCache перестанет ловить эту поломку. При смене passable_UnloadedCachedChunk_ReadsCache проверь это утверждение.");
        assertEquals(
                NavActionCosts.COST_INF,
                world.breakTicks(16, -1, 0),
                0,
                "твёрдый блок из кэша без данных о прочности не ломаем"
                        + " Иначе тест passable_UnloadedCachedChunk_ReadsCache перестанет ловить эту поломку. При смене passable_UnloadedCachedChunk_ReadsCache проверь это утверждение.");
    }

    @Test
    void available_UnloadedUnknownChunk_IsFalse() {
        NavTestWorld live = NavTestWorld.flat(0).unloadFrom(1);
        NavCacheWorld world = new NavCacheWorld(live, new NavCachedWorld());

        assertFalse(
                world.available(16, 0),
                "незагруженный и некэшированный чанк недоступен"
                        + " Иначе тест available_UnloadedUnknownChunk_IsFalse перестанет ловить эту поломку. При смене available_UnloadedUnknownChunk_IsFalse проверь это утверждение.");
        assertFalse(
                world.passable(16, 1, 0),
                "без данных блок не считается проходимым"
                        + " Иначе тест available_UnloadedUnknownChunk_IsFalse перестанет ловить эту поломку. При смене available_UnloadedUnknownChunk_IsFalse проверь это утверждение.");
        assertFalse(
                world.walkable(16, -1, 0),
                "без данных пол не выдумывается"
                        + " Иначе тест available_UnloadedUnknownChunk_IsFalse перестанет ловить эту поломку. При смене available_UnloadedUnknownChunk_IsFalse проверь это утверждение.");
    }
}
