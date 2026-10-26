package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FenceStripTests {
    @Test
    void of_LineOfPosts_CoversLineAndOneCellEachSide() {
        FenceLine ring = new FenceLine(
                List.of(new ColumnMask.Cell(0, 0), new ColumnMask.Cell(1, 0), new ColumnMask.Cell(2, 0)), List.of());
        ColumnMask strip = FenceStrip.of(ring, 1);
        assertEquals(
                15,
                strip.count(),
                "Полоса ограды шириной 1 — линия столбов и по клетке с каждой стороны (3×5): забор встаёт на одну "
                        + "высоту только на выровненной полосе (INC-53, INC-91)");
        assertTrue(strip.contains(1, -1) && strip.contains(1, 1), "Клетки по обе стороны линии входят в полосу");
        assertFalse(
                strip.contains(1, 2), "Вторая клетка за линией при ширине 1 не входит: выравнивание не шире данных");
    }

    @Test
    void load_TownRules_FenceStripPositive() {
        TownRules rules = TownRules.load();
        assertTrue(
                rules.spacing().fenceStrip() >= 1,
                "Полоса ограды берётся из town.json и не нулевая: без неё забор идёт по рельефу на разных высотах");
    }
}
