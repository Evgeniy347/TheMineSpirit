package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SchematicUpperFloors {
    private static final int UPPER_FLOOR_RISE = 3;
    private static final int UPPER_FLOOR_MIN_CELLS = 6;
    private static final int GRAND_STAIRS_LEVEL = 3;

    private SchematicUpperFloors() {}

    static void checkStairs(
            SchematicRules.Structure walls,
            Map<BlockCoord, String> grid,
            SchematicWalk.Result walk,
            int level,
            List<SchematicRules.Violation> violations) {
        List<Set<BlockCoord>> floors = upperFloors(walls, grid);
        if (floors.isEmpty()) {
            return;
        }
        for (Set<BlockCoord> floor : floors) {
            if (floor.stream().noneMatch(walk.feet()::contains)) {
                BlockCoord any = floor.iterator().next();
                violations.add(
                        new SchematicRules.Violation(
                                SchematicRules.RuleId.STAIRS,
                                "этаж на высоте y=" + any.y() + " (" + floor.size()
                                        + " клеток) не достижим от входа по "
                                        + "ступенькам: нужна нормальная лестница, не приставная и без прыжков (владелец 19.10)"));
                return;
            }
        }
        if (level >= GRAND_STAIRS_LEVEL && !wideStairs(walk.risers())) {
            violations.add(new SchematicRules.Violation(
                    SchematicRules.RuleId.STAIRS,
                    "у постройки уровня " + level + " с этажами нет лестницы шириной 2: парадная лестница — "
                            + "две ступеньки рядом на каждом шаге (владелец 19.10)"));
        }
    }

    static boolean wideStairs(Set<BlockCoord> risers) {
        for (BlockCoord riser : risers) {
            for (BlockCoord next : neighbours(riser)) {
                if (next.y() == riser.y() && risers.contains(next)) {
                    return true;
                }
            }
        }
        return false;
    }

    static List<Set<BlockCoord>> upperFloors(SchematicRules.Structure walls, Map<BlockCoord, String> grid) {
        Schematic schematic = walls.schematic();
        int minY = walls.entry().y() + UPPER_FLOOR_RISE;
        Set<BlockCoord> cells = new HashSet<>();
        for (int x = 1; x < schematic.sizeX() - 1; x++) {
            for (int z = 1; z < schematic.sizeZ() - 1; z++) {
                for (int y = minY; y < schematic.sizeY(); y++) {
                    BlockCoord cell = new BlockCoord(x, y, z);
                    if (SchematicWalk.standableIn(grid, schematic, cell)
                            && SchematicLevelRules.coveredAbove(cell, schematic, grid)) {
                        cells.add(cell);
                    }
                }
            }
        }
        List<Set<BlockCoord>> floors = new ArrayList<>();
        Set<BlockCoord> seen = new HashSet<>();
        for (BlockCoord cell : cells) {
            if (!seen.add(cell)) {
                continue;
            }
            Set<BlockCoord> floor = new HashSet<>();
            Deque<BlockCoord> queue = new ArrayDeque<>(List.of(cell));
            while (!queue.isEmpty()) {
                BlockCoord current = queue.poll();
                floor.add(current);
                for (BlockCoord next : neighbours(current)) {
                    if (next.y() == current.y() && cells.contains(next) && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            if (floor.size() >= UPPER_FLOOR_MIN_CELLS && hasSquare(floor)) {
                floors.add(floor);
            }
        }
        return floors;
    }

    static boolean hasSquare(Set<BlockCoord> floor) {
        for (BlockCoord cell : floor) {
            if (floor.contains(new BlockCoord(cell.x() + 1, cell.y(), cell.z()))
                    && floor.contains(new BlockCoord(cell.x(), cell.y(), cell.z() + 1))
                    && floor.contains(new BlockCoord(cell.x() + 1, cell.y(), cell.z() + 1))) {
                return true;
            }
        }
        return false;
    }

    static List<BlockCoord> neighbours(BlockCoord cell) {
        return List.of(
                new BlockCoord(cell.x() + 1, cell.y(), cell.z()),
                new BlockCoord(cell.x() - 1, cell.y(), cell.z()),
                new BlockCoord(cell.x(), cell.y(), cell.z() + 1),
                new BlockCoord(cell.x(), cell.y(), cell.z() - 1),
                new BlockCoord(cell.x(), cell.y() + 1, cell.z()),
                new BlockCoord(cell.x(), cell.y() - 1, cell.z()));
    }

    static boolean onPerimeter(BlockCoord cell, Schematic schematic) {
        return cell.x() == 0 || cell.x() == schematic.sizeX() - 1 || cell.z() == 0 || cell.z() == schematic.sizeZ() - 1;
    }
}
