package com.theminespirit.core.board.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotLeveling;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteTrailTests {
    @Test
    void of_PathFill_CollectsEveryCellAndMaterial() {
        PlotLeveling path = new PlotLeveling(
                List.of(),
                List.of(new PlotLeveling.Box(0, 64, 0, 0, 64, 0), new PlotLeveling.Box(1, 64, 0, 1, 64, 0)),
                "sand");

        RouteTrail trail = RouteTrail.of(path);

        assertEquals(2, trail.cells().size(), "Тропа помнит каждую проложенную клетку: иначе старый след не найти");
        assertEquals("sand", trail.block(), "Материал тропы сохраняется: снимают тот блок, который ставили");
    }

    @Test
    void removal_CutsTrailCells_WithoutFillingOrVoids() {
        RouteTrail trail = new RouteTrail(List.of(new BlockCoord(10, 64, 20)), "dirt");

        PlotLeveling removal = trail.removal();

        assertEquals(1, removal.cutVolume(), "Снятие срезает проложенный блок, а не засыпает его сверху");
        assertEquals(0, removal.fillVolume(), "Снятие ничего не подсыпает: земля под тропой уже на месте");
        assertTrue(removal.voids().isEmpty(), "Снятие не оставляет пустот: старую тропу убирают до земли");
    }

    @Test
    void removal_StraightTrail_MergesIntoOneRun() {
        List<BlockCoord> cells = RoutePath.tiles(new BlockCoord(0, 64, 0), new BlockCoord(6, 64, 0)).stream()
                .map(tile -> new BlockCoord(tile.x(), 64, tile.z()))
                .toList();
        RouteTrail trail = new RouteTrail(cells, "dirt");

        PlotLeveling removal = trail.removal();

        assertEquals(cells.size(), removal.cutVolume(), "Снятие покрывает все клетки тропы без пропусков");
        assertEquals(
                3,
                removal.cutTopDown().size(),
                "Тропа ширины три снимается тремя полосами, а не клеткой на вызов: сотни одиночных "
                        + "clear_area не влезают в задание");
    }

    @Test
    void first_LowestThenWestCell_IsStartOfWork() {
        RouteTrail trail = new RouteTrail(
                List.of(new BlockCoord(5, 70, 0), new BlockCoord(2, 64, 3), new BlockCoord(1, 64, 3)), "dirt");

        assertEquals(
                new BlockCoord(1, 64, 3),
                trail.first(),
                "Работа начинается с нижней западной клетки: дух идёт к ближнему концу старой тропы");
    }
}
