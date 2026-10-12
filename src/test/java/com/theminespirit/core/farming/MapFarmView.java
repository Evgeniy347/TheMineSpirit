package com.theminespirit.core.farming;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class MapFarmView implements FarmView {
    private static final FarmTile DEFAULT = new FarmTile(Ground.OTHER, Above.BLOCKED, Optional.empty());

    private final Map<BlockCoord, FarmTile> tiles = new HashMap<>();
    private int reads;

    MapFarmView set(int x, int y, int z, FarmTile tile) {
        tiles.put(new BlockCoord(x, y, z), tile);
        return this;
    }

    MapFarmView fillBed(int y, int x1, int z1, int x2, int z2, FarmTile tile) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                tiles.put(new BlockCoord(x, y, z), tile);
            }
        }
        return this;
    }

    FarmTile peek(BlockCoord coord) {
        return tiles.getOrDefault(coord, DEFAULT);
    }

    int reads() {
        return reads;
    }

    @Override
    public FarmTile tileAt(BlockCoord coord) {
        reads++;
        return tiles.getOrDefault(coord, DEFAULT);
    }
}
