package com.theminespirit.core.perception;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;

final class GridTerrain implements TerrainView {
    private final Terrain fallback;
    private final Map<BlockCoord, Terrain> cells = new HashMap<>();
    private int reads;

    GridTerrain(Terrain fallback) {
        this.fallback = fallback;
    }

    GridTerrain with(int x, int y, int z, Terrain terrain) {
        cells.put(new BlockCoord(x, y, z), terrain);
        return this;
    }

    GridTerrain column(int x, int z, int fromY, int toY, Terrain terrain) {
        for (int y = fromY; y <= toY; y++) {
            with(x, y, z, terrain);
        }
        return this;
    }

    @Override
    public Terrain at(BlockCoord coord) {
        reads++;
        return cells.getOrDefault(coord, fallback);
    }

    int reads() {
        return reads;
    }
}
