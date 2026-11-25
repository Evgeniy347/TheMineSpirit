package com.theminespirit.core.town;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class WallRegion {
    private WallRegion() {}

    public static ColumnMask of(List<TownMap.WallSegment> wall) {
        Objects.requireNonNull(wall, "Стена не может быть null: граница поселения считается по ней");
        if (wall.isEmpty()) {
            throw new IllegalArgumentException("У плана нет стены: граница поселения задаётся только замкнутым "
                    + "забором — нарисуйте его и загрузите план заново");
        }
        Set<ColumnMask.Cell> cells = new HashSet<>();
        for (TownMap.WallSegment segment : wall) {
            if (!WallSegmentCells.valid(segment)) {
                throw new IllegalArgumentException("Отрезок стены " + segment.x1() + "," + segment.z1() + " → "
                        + segment.x2() + "," + segment.z2() + " не прямой и не под 45°: границу не замкнуть");
            }
            cells.addAll(WallSegmentCells.of(segment));
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (ColumnMask.Cell cell : cells) {
            minX = Math.min(minX, cell.x());
            minZ = Math.min(minZ, cell.z());
            maxX = Math.max(maxX, cell.x());
            maxZ = Math.max(maxZ, cell.z());
        }
        int x1 = minX - 1;
        int z1 = minZ - 1;
        int width = maxX - minX + 3;
        int depth = maxZ - minZ + 3;
        boolean[][] wallCells = new boolean[width][depth];
        for (ColumnMask.Cell cell : cells) {
            wallCells[cell.x() - x1][cell.z() - z1] = true;
        }
        boolean[][] outside = new boolean[width][depth];
        Deque<ColumnMask.Cell> queue = new ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            add(queue, outside, x, 0, wallCells);
            add(queue, outside, x, depth - 1, wallCells);
        }
        for (int z = 0; z < depth; z++) {
            add(queue, outside, 0, z, wallCells);
            add(queue, outside, width - 1, z, wallCells);
        }
        spread(queue, outside, wallCells, width, depth);
        BitSet bits = new BitSet(width * depth);
        int inside = 0;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                boolean wet = wallCells[x][z];
                if (wet || !outside[x][z]) {
                    bits.set(z * width + x);
                    inside += wet ? 0 : 1;
                }
            }
        }
        if (inside == 0) {
            throw new IllegalArgumentException("Стена не замкнута: у контура есть дырка, и вода снаружи затекает "
                    + "внутрь — закройте забор и загрузите план заново");
        }
        return new ColumnMask(x1, z1, width, depth, bits);
    }

    private static void add(Deque<ColumnMask.Cell> queue, boolean[][] outside, int x, int z, boolean[][] wall) {
        if (!wall[x][z] && !outside[x][z]) {
            outside[x][z] = true;
            queue.add(new ColumnMask.Cell(x, z));
        }
    }

    private static void spread(Deque<ColumnMask.Cell> queue, boolean[][] outside, boolean[][] wall, int w, int h) {
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            ColumnMask.Cell cell = queue.poll();
            for (int side = 0; side < dx.length; side++) {
                int x = cell.x() + dx[side];
                int z = cell.z() + dz[side];
                if (x >= 0 && z >= 0 && x < w && z < h) {
                    add(queue, outside, x, z, wall);
                }
            }
        }
    }
}
