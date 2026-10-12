package com.theminespirit.core.movement;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;

final class CountingSafety implements SafetyView {
    private final Map<BlockCoord, Cell> cells = new HashMap<>();
    private int reads;

    CountingSafety set(int x, int y, int z, Cell cell) {
        cells.put(new BlockCoord(x, y, z), cell);
        return this;
    }

    CountingSafety fillSolid(int y, int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                cells.put(new BlockCoord(x, y, z), Cell.SOLID);
            }
        }
        return this;
    }

    int reads() {
        return reads;
    }

    @Override
    public Cell cellAt(BlockCoord coord) {
        reads++;
        return cells.getOrDefault(coord, Cell.PASSABLE);
    }
}
