package com.theminespirit.core.town;

import java.util.Objects;
import java.util.Optional;

public final class PlotCells {
    public static final int FREE_HEIGHT = 3;

    public enum Expect {
        GROUND,
        BUILDING,
        MINE,
        FREE,
        ANY
    }

    public enum Found {
        OPEN,
        NATURAL,
        COBBLESTONE,
        OTHER
    }

    public enum Defect {
        HOLE,
        PILLAR,
        EXTRA
    }

    public record Cell(int dy, boolean mine, boolean planned) {}

    private PlotCells() {}

    public static Expect expect(Cell cell) {
        Objects.requireNonNull(cell, "Клетка участка не может быть null");
        if (cell.mine()) {
            return Expect.MINE;
        }
        if (cell.planned()) {
            return Expect.BUILDING;
        }
        if (cell.dy() == -1) {
            return Expect.GROUND;
        }
        if (cell.dy() >= 0 && cell.dy() < FREE_HEIGHT) {
            return Expect.FREE;
        }
        return Expect.ANY;
    }

    public static Optional<Defect> defect(Cell cell, Found found) {
        Objects.requireNonNull(found, "Найденный блок клетки не может быть null");
        Expect expected = expect(cell);
        if (expected == Expect.GROUND && found == Found.OPEN) {
            return Optional.of(Defect.HOLE);
        }
        if (expected == Expect.GROUND && found == Found.COBBLESTONE) {
            return Optional.of(Defect.EXTRA);
        }
        if (expected == Expect.FREE && found == Found.NATURAL) {
            return Optional.of(Defect.EXTRA);
        }
        if (expected == Expect.ANY
                && cell.dy() >= FREE_HEIGHT
                && !cell.mine()
                && !cell.planned()
                && found != Found.OPEN) {
            return Optional.of(Defect.PILLAR);
        }
        return Optional.empty();
    }
}
