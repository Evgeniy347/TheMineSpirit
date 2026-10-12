package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.Terrain;
import com.theminespirit.core.perception.TerrainView;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class FakeTerrain implements TerrainView {
    static final int HALF = 30;

    private final Map<BlockCoord, Terrain> overrides = new HashMap<>();
    private final Set<Long> unloadedColumns = new HashSet<>();
    private int reads;

    static FakeTerrain flat() {
        return new FakeTerrain();
    }

    FakeTerrain set(int x, int y, int z, Terrain terrain) {
        overrides.put(new BlockCoord(x, y, z), terrain);
        return this;
    }

    FakeTerrain wall(int x, int z, int height) {
        for (int y = 1; y <= height; y++) {
            set(x, y, z, Terrain.SOLID);
        }
        return this;
    }

    FakeTerrain unloaded(int x, int z) {
        unloadedColumns.add(key(x, z));
        return this;
    }

    @Override
    public Terrain at(BlockCoord coord) {
        reads++;
        if (unloadedColumns.contains(key(coord.x(), coord.z()))) {
            return Terrain.UNLOADED;
        }
        Terrain override = overrides.get(coord);
        if (override != null) {
            return override;
        }
        return coord.y() <= 0 && Math.abs(coord.x()) <= HALF && Math.abs(coord.z()) <= HALF ? Terrain.SOLID : Terrain.AIR;
    }

    int reads() {
        return reads;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
