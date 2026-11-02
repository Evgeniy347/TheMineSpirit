// lib: Из Baritone (github.com/cabaletta/baritone, src/test/java/baritone/pathing/calc/openset/OpenSetsTest.java),
// лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class NavTestWorld implements NavWorld {
    private static final double BREAK_TICKS = 10;

    private final int ground;
    private final Map<Long, Kind> changes = new HashMap<>();
    private final Set<Long> unloaded = new HashSet<>();
    private int unloadFrom = Integer.MAX_VALUE;

    private enum Kind {
        AIR,
        SOLID,
        BREAKABLE,
        WATER,
        CLIMBABLE
    }

    private NavTestWorld(int ground) {
        this.ground = ground;
    }

    static NavTestWorld flat(int ground) {
        return new NavTestWorld(ground);
    }

    NavTestWorld air(int x, int y, int z) {
        return set(x, y, z, Kind.AIR);
    }

    NavTestWorld solid(int x, int y, int z) {
        return set(x, y, z, Kind.SOLID);
    }

    NavTestWorld breakable(int x, int y, int z) {
        return set(x, y, z, Kind.BREAKABLE);
    }

    NavTestWorld wet(int x, int y, int z) {
        return set(x, y, z, Kind.WATER);
    }

    NavTestWorld climb(int x, int y, int z) {
        return set(x, y, z, Kind.CLIMBABLE);
    }

    NavTestWorld unload(int chunkX, int chunkZ) {
        unloaded.add(NavNode.key(chunkX, 0, chunkZ));
        return this;
    }

    NavTestWorld unloadFrom(int chunkX) {
        unloadFrom = chunkX;
        return this;
    }

    private NavTestWorld set(int x, int y, int z, Kind kind) {
        changes.put(NavNode.key(x, y, z), kind);
        return this;
    }

    private Kind kind(int x, int y, int z) {
        Kind kind = changes.get(NavNode.key(x, y, z));
        return kind == null ? (y <= ground ? Kind.SOLID : Kind.AIR) : kind;
    }

    @Override
    public boolean available(int x, int z) {
        return (x >> 4) < unloadFrom && !unloaded.contains(NavNode.key(x >> 4, 0, z >> 4));
    }

    @Override
    public boolean passable(int x, int y, int z) {
        return kind(x, y, z) == Kind.AIR;
    }

    @Override
    public boolean walkable(int x, int y, int z) {
        Kind kind = kind(x, y, z);
        return kind == Kind.SOLID || kind == Kind.BREAKABLE;
    }

    @Override
    public boolean water(int x, int y, int z) {
        return kind(x, y, z) == Kind.WATER;
    }

    @Override
    public boolean climbable(int x, int y, int z) {
        return kind(x, y, z) == Kind.CLIMBABLE;
    }

    @Override
    public double breakTicks(int x, int y, int z) {
        return kind(x, y, z) == Kind.BREAKABLE ? BREAK_TICKS : NavActionCosts.COST_INF;
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
