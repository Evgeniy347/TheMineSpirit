package com.theminespirit.core.farming;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;

final class CountingSafety implements SafetyView {
    private final Map<BlockCoord, Cell> cells = new HashMap<>();
    private final Map<BlockCoord, Integer> perCell = new HashMap<>();
    private int reads;

    CountingSafety set(int x, int y, int z, Cell cell) {
        cells.put(new BlockCoord(x, y, z), cell);
        return this;
    }

    CountingSafety fillFloor(int y, int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                cells.put(new BlockCoord(x, y, z), Cell.SOLID);
            }
        }
        return this;
    }

    CountingSafety fill(int y, int x1, int z1, int x2, int z2, Cell cell) {
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

    int maxReadsPerCell() {
        return perCell.values().stream().mapToInt(count -> count).max().orElse(0);
    }

    @Override
    public Cell cellAt(BlockCoord coord) {
        reads++;
        perCell.merge(coord, 1, Integer::sum);
        return cells.getOrDefault(coord, Cell.PASSABLE);
    }
}
