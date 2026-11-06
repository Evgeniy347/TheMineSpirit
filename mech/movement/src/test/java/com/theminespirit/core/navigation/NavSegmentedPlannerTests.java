// lib: Из Baritone (github.com/cabaletta/baritone, behavior/PathingBehavior.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class NavSegmentedPlannerTests {
    private static final int MIN_Y = -16;
    private static final int HEIGHT = 32;

    @Test
    void flatWorld_SmallSegments_ReachFarGoal() {
        NavContext context = context(NavTestWorld.flat(0), 40);

        NavSegmentedRoute route =
                NavSegmentedPlanner.follow(context, new BlockCoord(0, 1, 0), new NavGoalBlock(100, 1, 0), 50, 6, 0.5);

        assertEquals(NavSearchStatus.FOUND, route.status(), "сегменты доходят до цели");
        assertTrue(route.segments() > 1, "цель достигнута несколькими сегментами: " + route.segments());
        assertEquals(100, route.end().x(), "конец совпадает с целью");
        assertTrue(
                route.positions().size() <= 101,
                "позиции не дублируются на стыках: " + route.positions().size());
    }

    @Test
    void cachedUnloadedChunks_SegmentedPath_CrossesBorder() {
        NavTestWorld full = NavTestWorld.flat(0);
        NavCachedWorld cache = new NavCachedWorld();
        for (int chunkX = 0; chunkX <= 8; chunkX++) {
            cache.put(NavCachedChunk.pack(chunkX, 0, MIN_Y, HEIGHT, source(full)));
        }
        NavContext context = context(new NavCacheWorld(NavTestWorld.flat(0).unloadFrom(1), cache), 80);

        NavSegmentedRoute route =
                NavSegmentedPlanner.follow(context, new BlockCoord(0, 1, 0), new NavGoalBlock(128, 1, 0), 50, 6, 0.5);

        assertEquals(NavSearchStatus.FOUND, route.status(), "кэш позволяет пройти незагруженные чанки сегментами");
        assertEquals(128, route.end().x(), "цель за границей загруженного достигнута");
    }

    @Test
    void unloadedWithoutCache_SegmentedPath_StopsAtBorder() {
        NavContext context = context(NavTestWorld.flat(0).unloadFrom(1), 80);

        NavSegmentedRoute route =
                NavSegmentedPlanner.follow(context, new BlockCoord(0, 1, 0), new NavGoalBlock(128, 1, 0), 50, 6, 0.5);

        assertEquals(NavSearchStatus.SEGMENT, route.status(), "без кэша остаётся сегмент до границы");
        assertTrue(route.end().x() < 16, "поиск не заходит в незагруженный чанк: " + route.end());
        assertTrue(route.segments() <= 2, "после границы нет прогресса — сегменты прекращаются: " + route.segments());
    }

    private static NavContext context(NavWorld world, int maxNodes) {
        NavSettings settings = new NavSettings(10_000, 10_000, maxNodes, 50, 3, 12, 2, 20, 2, true, true);
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
