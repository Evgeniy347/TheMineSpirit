package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.reload.DataTables;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WallLayoutTolerantTests {
    @Test
    void closedOrEmpty_UnlayoutableGates_ReturnsEmptyInsteadOfCrashing() {
        List<WallPiece> pieces = WallLayout.of(DataTables.INSTANCE.presets().current(), StageSchemes.FENCE)
                .closedOrEmpty(List.of(new ColumnMask.Cell(0, 0)), Set.of(0));

        assertEquals(
                List.of(),
                pieces,
                "Нераскладываемая линия ограды не должна валить источник доски: двор и шахта остаются без "
                        + "графа и ремонта, если источник падает каждый тик");
    }
}
