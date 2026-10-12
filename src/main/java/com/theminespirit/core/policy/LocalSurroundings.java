package com.theminespirit.core.policy;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Optional;

public final class LocalSurroundings implements SafetyView {
    public static final int SIDE = 3;
    public static final int READS = SIDE * SIDE * SIDE;
    public static final Cell UNSEEN_CELL = Cell.PASSABLE;

    private final BlockCoord feet;
    private final Cell[] cells;
    private final BodyFlags flags;
    private final Optional<BlockCoord> nearestWater;

    private LocalSurroundings(BlockCoord feet, Cell[] cells, BodyFlags flags, Optional<BlockCoord> nearestWater) {
        this.feet = feet;
        this.cells = cells;
        this.flags = flags;
        this.nearestWater = nearestWater;
    }

    public static LocalSurroundings read(BlockCoord feet, SafetyView view, BodyFlags flags, Optional<BlockCoord> nearestWater) {
        Objects.requireNonNull(feet, "Клетка ног не может быть null: вокруг неё читается снимок 3×3×3");
        Objects.requireNonNull(view, "SafetyView не может быть null: по нему читаются 27 клеток снимка");
        Objects.requireNonNull(flags, "Флаги тела не могут быть null: по ним рефлексы яруса 0 решают, гореть ли духу");
        Objects.requireNonNull(nearestWater, "Ближайшая вода задаётся Optional.empty(), а не null");
        Cell[] cells = new Cell[READS];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    cells[index(dx, dy, dz)] = Objects.requireNonNull(
                            view.cellAt(new BlockCoord(feet.x() + dx, feet.y() + dy, feet.z() + dz)),
                            "SafetyView вернул null для клетки снимка: классификатор блоков обязан вернуть Cell");
                }
            }
        }
        return new LocalSurroundings(feet, cells, flags, nearestWater);
    }

    public Cell cellAt(int dx, int dy, int dz) {
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || Math.abs(dz) > 1) {
            throw new IllegalArgumentException("Смещение (" + dx + ", " + dy + ", " + dz + ") вне снимка 3×3×3: допустимы значения от -1 до 1");
        }
        return cells[index(dx, dy, dz)];
    }

    @Override
    public Cell cellAt(BlockCoord coord) {
        int dx = coord.x() - feet.x();
        int dy = coord.y() - feet.y();
        int dz = coord.z() - feet.z();
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || Math.abs(dz) > 1) {
            return UNSEEN_CELL;
        }
        return cells[index(dx, dy, dz)];
    }

    public BlockCoord feet() {
        return feet;
    }

    public BodyFlags flags() {
        return flags;
    }

    public Optional<BlockCoord> nearestWater() {
        return nearestWater;
    }

    private static int index(int dx, int dy, int dz) {
        return ((dx + 1) * SIDE + (dy + 1)) * SIDE + (dz + 1);
    }
}
