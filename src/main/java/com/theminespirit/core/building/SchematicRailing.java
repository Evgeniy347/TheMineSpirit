package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class SchematicRailing {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONALS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private SchematicRailing() {}

    static void check(
            SchematicRules.Structure structure,
            Map<BlockCoord, String> grid,
            SchematicWalk.Result walk,
            List<SchematicRules.Violation> violations) {
        Schematic schematic = structure.schematic();
        int base = structure.entry().y();
        Set<BlockCoord> drops = new HashSet<>();
        for (int y = base + 1; y < schematic.sizeY(); y++) {
            for (int z = 0; z < schematic.sizeZ(); z++) {
                for (int x = 0; x < schematic.sizeX(); x++) {
                    BlockCoord cell = new BlockCoord(x, y, z);
                    if (isDrop(grid, schematic, cell)) {
                        drops.add(cell);
                    }
                }
            }
        }
        if (drops.isEmpty()) {
            return;
        }
        Set<BlockCoord> feet = walk.feet();
        Set<BlockCoord> edges = new HashSet<>();
        Set<BlockCoord> missing = new HashSet<>();
        for (BlockCoord drop : drops) {
            for (int[] side : SIDES) {
                BlockCoord cell = at(drop, side[0], 0, side[1]);
                if (!supportedFloor(grid, cell)) {
                    continue;
                }
                String block = grid.get(cell);
                if (isOpening(block)) {
                    continue;
                }
                boolean railing = isRailing(block);
                boolean passable = SchematicWalk.passable(block);
                if (!railing && !passable) {
                    continue;
                }
                edges.add(cell);
                if (passable && feet.contains(cell)) {
                    missing.add(cell);
                }
            }
        }
        for (BlockCoord cell : feet) {
            if (cell.y() <= base || !supportedFloor(grid, cell)) {
                continue;
            }
            for (int[] diagonal : DIAGONALS) {
                if (!drops.contains(at(cell, diagonal[0], 0, diagonal[1]))) {
                    continue;
                }
                if (edges.contains(at(cell, diagonal[0], 0, 0)) && edges.contains(at(cell, 0, 0, diagonal[1]))) {
                    missing.add(cell);
                }
            }
        }
        List<BlockCoord> ordered = new ArrayList<>(missing);
        ordered.sort(BlockCoord::compareTo);
        for (BlockCoord cell : ordered) {
            violations.add(new SchematicRules.Violation(
                    SchematicRules.RuleId.RAILING,
                    "клетка [" + cell.x() + "," + cell.y() + "," + cell.z() + "] — край пола над обрывом: отсюда "
                            + "можно упасть, поставьте ограждение (забор) по краю (владелец 20.10)"));
        }
    }

    private static boolean isDrop(Map<BlockCoord, String> grid, Schematic schematic, BlockCoord cell) {
        if (cell.x() < 0
                || cell.y() < 0
                || cell.z() < 0
                || cell.x() >= schematic.sizeX()
                || cell.y() >= schematic.sizeY()
                || cell.z() >= schematic.sizeZ()) {
            return false;
        }
        if (!SchematicWalk.passable(grid.get(cell)) || !SchematicWalk.passable(grid.get(at(cell, 0, 1, 0)))) {
            return false;
        }
        return !SchematicWalk.standableAt(grid, schematic, at(cell, 0, -1, 0))
                && !SchematicWalk.standableAt(grid, schematic, at(cell, 0, -2, 0));
    }

    private static boolean supportedFloor(Map<BlockCoord, String> grid, BlockCoord cell) {
        String below = grid.get(at(cell, 0, -1, 0));
        return below != null && SchematicBlocks.isFullBlock(below);
    }

    private static boolean isRailing(@Nullable String block) {
        if (block == null) {
            return false;
        }
        String path = SchematicBlocks.path(block);
        return path.endsWith("_fence") || path.endsWith("_wall") || "iron_bars".equals(path);
    }

    private static boolean isOpening(@Nullable String block) {
        if (block == null) {
            return false;
        }
        String path = SchematicBlocks.path(block);
        return SchematicBlocks.isDoor(block)
                || path.endsWith("_trapdoor")
                || path.endsWith("_fence_gate")
                || SchematicBlocks.isWindow(block);
    }

    private static BlockCoord at(BlockCoord cell, int dx, int dy, int dz) {
        return new BlockCoord(cell.x() + dx, cell.y() + dy, cell.z() + dz);
    }
}
