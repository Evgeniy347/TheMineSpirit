package com.theminespirit.core.town;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TownWallCheck {
    static final int MIN_CLEARANCE = 2;

    private TownWallCheck() {}

    static void validate(TownMap map, ColumnMask region, ColumnMask water, List<TownMapFinding> findings) {
        Set<ColumnMask.Cell> cells = new HashSet<>();
        boolean broken = false;
        for (TownMap.WallSegment segment : map.wall()) {
            if (!WallSegmentCells.valid(segment)) {
                findings.add(new TownMapFinding(
                        "wall",
                        "segment",
                        "отрезок стены " + segment.x1() + "," + segment.z1() + " → " + segment.x2() + "," + segment.z2()
                                + " не прямой и не под 45°"));
                broken = true;
                continue;
            }
            cells.addAll(WallSegmentCells.of(segment));
        }
        if (broken || cells.isEmpty()) {
            return;
        }
        boolean diagonal =
                map.wall().stream().anyMatch(segment -> segment.x1() != segment.x2() && segment.z1() != segment.z2());
        Set<ColumnMask.Cell> joints = new HashSet<>();
        for (TownMap.WallSegment segment : map.wall()) {
            if (segment.x1() != segment.x2() && segment.z1() != segment.z2()) {
                joints.add(new ColumnMask.Cell(segment.x1(), segment.z1()));
                joints.add(new ColumnMask.Cell(segment.x2(), segment.z2()));
            }
        }
        Map<ColumnMask.Cell, Integer> degrees = new HashMap<>();
        for (ColumnMask.Cell cell : cells) {
            if (!region.contains(cell.x(), cell.z())) {
                findings.add(new TownMapFinding(
                        "wall", "outside", "стена выходит за границу поселения в " + cell.x() + "," + cell.z()));
                return;
            }
            if (water.contains(cell.x(), cell.z())) {
                findings.add(
                        new TownMapFinding("wall", "water", "стена заходит в воду в " + cell.x() + "," + cell.z()));
                return;
            }
            int degree = 0;
            for (ColumnMask.Cell neighbour : around(cell)) {
                if (cells.contains(neighbour)) {
                    degree++;
                }
            }
            degrees.put(cell, degree);
        }
        for (Map.Entry<ColumnMask.Cell, Integer> entry : degrees.entrySet()) {
            int degree = entry.getValue();
            if (degree <= 1 || (degree > 2 && !(diagonal && nearJoint(joints, entry.getKey())))) {
                findings.add(new TownMapFinding(
                        "wall",
                        "loop",
                        "стена не замкнута: у клетки " + entry.getKey().x() + ","
                                + entry.getKey().z() + " " + degree + " соседей вместо двух"));
                return;
            }
        }
        Set<ColumnMask.Cell> seen = new HashSet<>();
        List<ColumnMask.Cell> queue = new ArrayList<>();
        queue.add(cells.iterator().next());
        seen.add(queue.get(0));
        while (!queue.isEmpty()) {
            ColumnMask.Cell cell = queue.remove(queue.size() - 1);
            for (ColumnMask.Cell neighbour : around(cell)) {
                if (cells.contains(neighbour) && seen.add(neighbour)) {
                    queue.add(neighbour);
                }
            }
        }
        if (seen.size() != cells.size()) {
            findings.add(new TownMapFinding(
                    "wall",
                    "loop",
                    "стена распадается на куски: нужен один замкнутый контур, а соединено " + seen.size() + " из "
                            + cells.size() + " клеток"));
            return;
        }
        clearance(map, cells, findings);
    }

    private static void clearance(TownMap map, Set<ColumnMask.Cell> cells, List<TownMapFinding> findings) {
        for (ColumnMask.Cell cell : cells) {
            for (TownMap.MapPlot plot : map.plots()) {
                int dx = Math.max(plot.minX() - cell.x(), cell.x() - (plot.minX() + plot.width() - 1));
                int dz = Math.max(plot.minZ() - cell.z(), cell.z() - (plot.minZ() + plot.depth() - 1));
                if (Math.max(dx, dz) < MIN_CLEARANCE) {
                    findings.add(new TownMapFinding(
                            "wall",
                            "clearance",
                            "стена подходит к участку '" + plot.id() + "' ближе " + MIN_CLEARANCE + " блоков в "
                                    + cell.x() + "," + cell.z()));
                    return;
                }
            }
        }
    }

    private static List<ColumnMask.Cell> around(ColumnMask.Cell cell) {
        return List.of(
                new ColumnMask.Cell(cell.x() + 1, cell.z()),
                new ColumnMask.Cell(cell.x() - 1, cell.z()),
                new ColumnMask.Cell(cell.x(), cell.z() + 1),
                new ColumnMask.Cell(cell.x(), cell.z() - 1));
    }

    private static boolean nearJoint(Set<ColumnMask.Cell> joints, ColumnMask.Cell cell) {
        for (ColumnMask.Cell joint : joints) {
            if (Math.abs(joint.x() - cell.x()) <= 1 && Math.abs(joint.z() - cell.z()) <= 1) {
                return true;
            }
        }
        return false;
    }
}
