package com.theminespirit.core.town;

import java.util.ArrayList;
import java.util.List;

final class WallSegmentCells {
    private WallSegmentCells() {}

    static boolean valid(TownMap.WallSegment segment) {
        int dx = Math.abs(segment.x2() - segment.x1());
        int dz = Math.abs(segment.z2() - segment.z1());
        return dx == 0 || dz == 0 || dx == dz;
    }

    static List<ColumnMask.Cell> of(TownMap.WallSegment segment) {
        int dx = Integer.signum(segment.x2() - segment.x1());
        int dz = Integer.signum(segment.z2() - segment.z1());
        int steps = Math.max(Math.abs(segment.x2() - segment.x1()), Math.abs(segment.z2() - segment.z1()));
        List<ColumnMask.Cell> cells = new ArrayList<>(steps * 2 + 1);
        if (steps == 0) {
            cells.add(new ColumnMask.Cell(segment.x1(), segment.z1()));
            return List.copyOf(cells);
        }
        if (dx == 0 || dz == 0) {
            for (int step = 0; step <= steps; step++) {
                cells.add(new ColumnMask.Cell(segment.x1() + dx * step, segment.z1() + dz * step));
            }
            return List.copyOf(cells);
        }
        for (int step = 0; step < steps; step++) {
            cells.add(new ColumnMask.Cell(segment.x1() + dx * step, segment.z1() + dz * (step + 1)));
            cells.add(new ColumnMask.Cell(segment.x1() + dx * (step + 1), segment.z1() + dz * (step + 1)));
        }
        return List.copyOf(cells);
    }
}
