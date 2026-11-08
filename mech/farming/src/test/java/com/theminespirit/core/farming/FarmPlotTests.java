package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import java.util.List;
import org.junit.jupiter.api.Test;

class FarmPlotTests {
    @Test
    void of_ReversedCorners_NormalizesToSamePlot() {
        FarmPlot forward = FarmPlot.of(2, 3, 4, 5);
        FarmPlot reversed = FarmPlot.of(4, 5, 2, 3);

        assertEquals(
                forward,
                reversed,
                "Углы в любом порядке обязаны давать один прямоугольник: иначе стратег вынужден сортировать их сам");
        assertEquals(3, forward.sideX(), "Сторона X прямоугольника 2..4 — три клетки: по ней считается объём грядки");
        assertEquals(3, forward.sideZ(), "Сторона Z прямоугольника 3..5 — три клетки: по ней считается объём грядки");
        assertEquals(9, forward.cells(), "Грядка 3×3 — девять клеток: каждая вспахивается и засевается один раз");
    }

    @Test
    void grounds_SharedLevel_AllOnOneLevel() {
        List<BlockCoord> grounds = FarmPlot.of(1, 1, 2, 2).grounds(63);

        assertEquals(
                4,
                grounds.size(),
                "Каждая клетка прямоугольника обязана попасть в цели: иначе часть грядки останется целиной");
        assertTrue(
                grounds.stream().allMatch(cell -> cell.y() == 63),
                "Все клетки грядки лежат на одном уровне земли: дух не копает склон");
    }

    @Test
    void sideAboveLimit_AboveMax_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FarmPlot.of(0, 0, CatalogLimits.FARM_PLOT_SIDE_MAX, 0),
                "Сторона больше предела обязана отвергаться: длинную грядку дух обрабатывает часами");
    }

    @Test
    void center_FarCoordinates_DoesNotOverflowInt() {
        FarmPlot plot = FarmPlot.of(1_500_000_000, 1_500_000_000, 1_500_000_010, 1_500_000_010);

        assertEquals(
                new BlockCoord(1_500_000_005, 64, 1_500_000_005),
                plot.center(64),
                "Середина грядки дальних координат считается через long: сумма двух int в (minX + maxX) / 2 "
                        + "переполняется, центр уезжает за край мира и поиск воды идёт не там");
    }

    @Test
    void searchRadius_FromCenter_CoversFarthestCorner() {
        FarmPlot plot = FarmPlot.of(0, 0, 4, 4);

        assertEquals(2, plot.center(64).x(), "Центр прямоугольника 0..4 по X — 2: вокруг него ищется вода");
        assertEquals(
                2,
                plot.searchRadius(64),
                "Радиус поиска от центра до дальнего угла 0..4 — 2 клетки: меньше оставит угол без воды");
    }
}
