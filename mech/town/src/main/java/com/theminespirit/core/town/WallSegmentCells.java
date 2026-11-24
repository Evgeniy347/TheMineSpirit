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

    static List<ColumnMask.Cell> line(List<TownMap.WallSegment> segments) {
        List<TownMap.WallSegment> ordered = new ArrayList<>();
        boolean[] used = new boolean[segments.size()];
        TownMap.WallSegment first = segments.get(0);
        ordered.add(first);
        used[0] = true;
        ColumnMask.Cell end = new ColumnMask.Cell(first.x2(), first.z2());
        for (int step = 1; step < segments.size(); step++) {
            int next = -1;
            boolean reverse = false;
            for (int index = 0; index < segments.size(); index++) {
                if (used[index]) {
                    continue;
                }
                TownMap.WallSegment segment = segments.get(index);
                if (segment.x1() == end.x() && segment.z1() == end.z()) {
                    next = index;
                    break;
                }
                if (segment.x2() == end.x() && segment.z2() == end.z()) {
                    next = index;
                    reverse = true;
                    break;
                }
            }
            if (next < 0) {
                return List.of();
            }
            TownMap.WallSegment segment = segments.get(next);
            used[next] = true;
            if (reverse) {
                segment = new TownMap.WallSegment(segment.x2(), segment.z2(), segment.x1(), segment.z1());
            }
            ordered.add(segment);
            end = new ColumnMask.Cell(segment.x2(), segment.z2());
        }
        List<ColumnMask.Cell> out = new ArrayList<>();
        for (TownMap.WallSegment segment : ordered) {
            for (ColumnMask.Cell cell : of(segment)) {
                if (out.isEmpty() || !out.get(out.size() - 1).equals(cell)) {
                    out.add(cell);
                }
            }
        }
        if (out.size() > 1 && out.get(0).equals(out.get(out.size() - 1))) {
            out.remove(out.size() - 1);
        }
        return List.copyOf(out);
    }
}
