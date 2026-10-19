package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Set;

public final class MiningSafety {

    private static final int[][] FACE_OFFSETS = {
            {0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}
    };
    private static final Set<Cell> FLUIDS = Set.of(Cell.WATER, Cell.LAVA);
    private static final Set<Cell> DANGEROUS = Set.of(Cell.WATER, Cell.LAVA, Cell.FIRE, Cell.HAZARD);

    private MiningSafety() {
    }

    public static BreakSafety canBreak(BlockCoord target, SafetyView view) {
        Objects.requireNonNull(target, "Цель ломки не может быть null: без неё неизвестно, какие грани проверять");
        Objects.requireNonNull(view, "SafetyView не может быть null: по нему проверяется соседняя жидкость");
        for (int[] offset : FACE_OFFSETS) {
            Cell cell = view.cellAt(offset(target, offset));
            if (FLUIDS.contains(cell)) {
                return new BreakSafety.Unsafe("рядом с блоком " + fluidName(cell)
                        + ": жидкость затечёт в полость и на духа, сначала осушите соседнюю клетку");
            }
        }
        return new BreakSafety.Safe();
    }

    public static boolean canStepFrom(BlockCoord feet, BlockCoord item, SafetyView view) {
        Objects.requireNonNull(feet, "Клетка духа не может быть null: без неё неизвестно, куда шагать");
        Objects.requireNonNull(item, "Клетка предмета не может быть null: без неё неизвестно, куда шагать");
        Objects.requireNonNull(view, "SafetyView не может быть null: по нему проверяется, годится ли клетка для шага");
        if (item.y() != feet.y()) {
            return false;
        }
        int dx = Math.abs(item.x() - feet.x());
        int dz = Math.abs(item.z() - feet.z());
        if (dx > 1 || dz > 1 || (dx == 0 && dz == 0)) {
            return false;
        }
        return canStand(item, view);
    }

    public static boolean canStand(BlockCoord cell, SafetyView view) {
        Objects.requireNonNull(cell, "Клетка стоянки не может быть null: без неё неизвестно, где проверять пол и ноги");
        Objects.requireNonNull(view, "SafetyView не может быть null: по нему проверяется пол и опасные соседи");
        if (view.cellAt(cell) != Cell.PASSABLE) {
            return false;
        }
        if (view.cellAt(offset(cell, 0, 1, 0)) != Cell.PASSABLE) {
            return false;
        }
        BlockCoord floor = offset(cell, 0, -1, 0);
        if (view.cellAt(floor) != Cell.SOLID) {
            return false;
        }
        return ringSafe(cell, view) && ringSafe(floor, view);
    }

    private static boolean ringSafe(BlockCoord center, SafetyView view) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    if (DANGEROUS.contains(view.cellAt(offset(center, dx, 0, dz)))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static BlockCoord offset(BlockCoord coord, int[] offset) {
        return offset(coord, offset[0], offset[1], offset[2]);
    }

    private static BlockCoord offset(BlockCoord coord, int dx, int dy, int dz) {
        return new BlockCoord(coord.x() + dx, coord.y() + dy, coord.z() + dz);
    }

    private static String fluidName(Cell cell) {
        return cell == Cell.LAVA ? "лава" : "вода";
    }
}
