package com.theminespirit.core.town;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class WaterPonds {
    static final int MAX_AREA = 16 * 16;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};

    private WaterPonds() {}

    static ColumnMask small(ColumnMask water, ColumnMask region) {
        Objects.requireNonNull(water, "Вода не может быть null: малые пруды ищутся по ней");
        Objects.requireNonNull(region, "Граница не может быть null: пруд вне поселения не застраивают");
        Set<ColumnMask.Cell> wet = new HashSet<>(water.cells());
        Set<ColumnMask.Cell> seen = new HashSet<>();
        BitSet allowed = new BitSet(water.width() * water.depth());
        for (ColumnMask.Cell start : wet) {
            if (!seen.add(start)) {
                continue;
            }
            List<ColumnMask.Cell> pond = new ArrayList<>();
            Deque<ColumnMask.Cell> queue = new ArrayDeque<>();
            queue.add(start);
            boolean closed = true;
            while (!queue.isEmpty()) {
                ColumnMask.Cell cell = queue.poll();
                pond.add(cell);
                if (cell.x() == water.minX()
                        || cell.x() == water.minX() + water.width() - 1
                        || cell.z() == water.minZ()
                        || cell.z() == water.minZ() + water.depth() - 1
                        || !region.contains(cell.x(), cell.z())) {
                    closed = false;
                }
                for (int side = 0; side < DX.length; side++) {
                    ColumnMask.Cell next = new ColumnMask.Cell(cell.x() + DX[side], cell.z() + DZ[side]);
                    if (wet.contains(next) && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            if (!closed || pond.size() >= MAX_AREA) {
                continue;
            }
            for (ColumnMask.Cell cell : pond) {
                allowed.set(at(water, cell.x(), cell.z()));
            }
        }
        return new ColumnMask(water.minX(), water.minZ(), water.width(), water.depth(), allowed);
    }

    private static int at(ColumnMask mask, int x, int z) {
        return (z - mask.minZ()) * mask.width() + x - mask.minX();
    }
}
