package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.reload.DataTables;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WallLayoutTolerantTests {
    @Test
    void closedOrAtomic_UnlayoutableGates_FallsBackToSingleCells() {
        List<WallPiece> pieces = WallLayout.of(DataTables.INSTANCE.presets().current(), StageSchemes.fenceAtomic(1))
                .closedOrAtomic(List.of(new ColumnMask.Cell(0, 0)), Set.of(0));

        assertEquals(1, pieces.size(), "одна клетка линии — один фрагмент даже при калитке: " + pieces);
        assertEquals("fence_gate", pieces.get(0).scheme(), "клетка калитки в запасной раскладке — калитка-столб");
        assertEquals(1, pieces.get(0).length(), "запасной фрагмент закрывает ровно свою клетку: " + pieces);
    }

    @Test
    void closedOrAtomic_UnlayoutablePost_FallsBackToSinglePost() {
        List<WallPiece> pieces = WallLayout.of(DataTables.INSTANCE.presets().current(), StageSchemes.fenceAtomic(1))
                .closedOrAtomic(List.of(new ColumnMask.Cell(0, 0)), Set.of());

        assertEquals(1, pieces.size(), "одна клетка линии без калитки — один фрагмент: " + pieces);
        assertEquals("fence_post", pieces.get(0).scheme(), "запасной фрагмент столба — fence_post: " + pieces);
        assertEquals(1, pieces.get(0).length(), "запасной столб закрывает ровно свою клетку: " + pieces);
    }

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
