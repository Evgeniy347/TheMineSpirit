package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.reload.DataTables;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WallLayoutTolerantTests {
    private static final List<String> FENCE = List.of(
            "fence_long",
            "fence_straight",
            "fence_post",
            "fence_corner",
            "fence_diagonal",
            "fence_corner45_left",
            "fence_corner45_right",
            "fence_wicket");

    @Test
    void closedOrEmpty_UnlayoutableGates_ReturnsEmptyInsteadOfCrashing() {
        List<WallPiece> pieces = WallLayout.of(DataTables.INSTANCE.presets().current(), FENCE)
                .closedOrEmpty(List.of(new ColumnMask.Cell(0, 0)), Set.of(0));

        assertEquals(
                List.of(),
                pieces,
                "Нераскладываемая линия ограды не должна валить источник доски: двор и шахта остаются без "
                        + "графа и ремонта, если источник падает каждый тик"
                        + " Иначе тест closedOrEmpty_UnlayoutableGates_ReturnsEmptyInsteadOfCrashing перестанет ловить эту поломку. При смене closedOrEmpty_UnlayoutableGates_ReturnsEmptyInsteadOfCrashing проверь это утверждение.");
    }
}
