// lib: Из Baritone (github.com/cabaletta/baritone, src/main/java/baritone/utils/BlockStateInterface.java), лицензия
// LGPL-3.0.
package com.theminespirit.core.navigation;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class NavCacheWorld implements NavWorld {
    private final NavWorld live;
    private final NavCachedWorld cache;

    public NavCacheWorld(NavWorld live, NavCachedWorld cache) {
        this.live = Objects.requireNonNull(live, "Живой мир не может быть null: загруженные чанки читаются из него");
        this.cache = Objects.requireNonNull(cache, "Кэш не может быть null: незагруженные чанки читаются из него");
    }

    @Override
    public boolean available(int x, int z) {
        return live.available(x, z) || cache.isCached(x, z);
    }

    @Override
    public boolean passable(int x, int y, int z) {
        if (live.available(x, z)) {
            return live.passable(x, y, z);
        }
        return cached(x, y, z) == NavBlockType.AIR;
    }

    @Override
    public boolean walkable(int x, int y, int z) {
        if (live.available(x, z)) {
            return live.walkable(x, y, z);
        }
        return cached(x, y, z) == NavBlockType.SOLID;
    }

    @Override
    public boolean water(int x, int y, int z) {
        if (live.available(x, z)) {
            return live.water(x, y, z);
        }
        return cached(x, y, z) == NavBlockType.WATER;
    }

    @Override
    public boolean climbable(int x, int y, int z) {
        return live.available(x, z) && live.climbable(x, y, z);
    }

    @Override
    public double breakTicks(int x, int y, int z) {
        if (live.available(x, z)) {
            return live.breakTicks(x, y, z);
        }
        return cached(x, y, z) == NavBlockType.SOLID ? NavActionCosts.COST_INF : 0;
    }

    @Override
    public int minY() {
        return live.minY();
    }

    @Override
    public int maxY() {
        return live.maxY();
    }

    public NavCachedWorld cache() {
        return cache;
    }

    private @Nullable NavBlockType cached(int x, int y, int z) {
        return cache.type(x, y, z);
    }
}
