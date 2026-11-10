package com.theminespirit.core.town;

import com.theminespirit.core.building.PresetLibrary;
import java.util.List;

public final class WallDemo {
    public static final int GATE = 20;

    private static final List<ColumnMask.Cell> CONTOUR = List.of(
            new ColumnMask.Cell(0, 0),
            new ColumnMask.Cell(1, 0),
            new ColumnMask.Cell(2, 0),
            new ColumnMask.Cell(3, 0),
            new ColumnMask.Cell(3, 1),
            new ColumnMask.Cell(4, 1),
            new ColumnMask.Cell(5, 1),
            new ColumnMask.Cell(6, 1),
            new ColumnMask.Cell(6, 2),
            new ColumnMask.Cell(7, 2),
            new ColumnMask.Cell(7, 3),
            new ColumnMask.Cell(7, 4),
            new ColumnMask.Cell(7, 5),
            new ColumnMask.Cell(6, 5),
            new ColumnMask.Cell(5, 5),
            new ColumnMask.Cell(4, 5),
            new ColumnMask.Cell(3, 5),
            new ColumnMask.Cell(3, 6),
            new ColumnMask.Cell(2, 6),
            new ColumnMask.Cell(1, 6),
            new ColumnMask.Cell(0, 6),
            new ColumnMask.Cell(-1, 6),
            new ColumnMask.Cell(-1, 5),
            new ColumnMask.Cell(-1, 4),
            new ColumnMask.Cell(-1, 3),
            new ColumnMask.Cell(-1, 2),
            new ColumnMask.Cell(-1, 1),
            new ColumnMask.Cell(0, 1));

    private WallDemo() {}

    public static FenceLine line() {
        return new FenceLine(CONTOUR, List.of(GATE));
    }

    public static List<WallPiece> pieces(PresetLibrary presets) {
        return WallLayout.of(presets, StageSchemes.FENCE).closed(line().posts(), line().gates());
    }
}
