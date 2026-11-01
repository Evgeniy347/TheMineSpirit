package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class MineWalls {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private MineWalls() {}

    public static List<BlockCoord> of(MineColumn column, Set<BlockCoord> walls) {
        Objects.requireNonNull(column, "Колонка не может быть null: без неё неизвестно, где стены");
        Objects.requireNonNull(walls, "Стенки не могут быть null: пустой набор — Set.of()");
        List<BlockCoord> found = new ArrayList<>();
        for (BlockCoord cell : column.cells()) {
            add(cell, walls, found);
        }
        add(column.floor(), walls, found);
        return found;
    }

    private static void add(BlockCoord cell, Set<BlockCoord> walls, List<BlockCoord> found) {
        for (int[] side : SIDES) {
            BlockCoord wall = new BlockCoord(cell.x() + side[0], cell.y(), cell.z() + side[1]);
            if (walls.contains(wall)) {
                found.add(wall);
            }
        }
    }
}
