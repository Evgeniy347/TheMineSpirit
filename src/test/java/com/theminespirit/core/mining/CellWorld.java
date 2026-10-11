package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;

final class CellWorld implements SafetyView {
    private final Map<BlockCoord, Cell> cells = new HashMap<>();
    private int reads;

    CellWorld set(int x, int y, int z, Cell cell) {
        cells.put(new BlockCoord(x, y, z), cell);
        return this;
    }

    CellWorld fill(int y, int x1, int z1, int x2, int z2, Cell cell) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                cells.put(new BlockCoord(x, y, z), cell);
            }
        }
        return this;
    }

    int reads() {
        return reads;
    }

    Cell peek(BlockCoord coord) {
        return cells.getOrDefault(coord, Cell.PASSABLE);
    }

    @Override
    public Cell cellAt(BlockCoord coord) {
        reads++;
        return cells.getOrDefault(coord, Cell.PASSABLE);
    }
}
