package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class AreaReplantTests {
    private static final ReplantData DATA = ReplantData.defaults();

    @Test
    void plan_FlatSoil_GivesGridCellsAroundBase() {
        TreeGrid grid = flatSoil();
        BlockCoord center = new BlockCoord(0, 1, 0);

        List<BlockCoord> cells = AreaReplant.plan(center, List.of(center), DATA, grid);

        assertEquals(
                24,
                cells.size(),
                "В радиусе 6 с шагом 3 вокруг центра — сетка 5x5 без самой клетки ствола: 24 клетки досадки. "
                        + "Фактически " + cells);
        assertFalse(
                cells.contains(center),
                "Клетка ствола исключена: там уже посажен саженец пересадки, второй в ту же клетку не встанет");
        assertTrue(
                cells.contains(new BlockCoord(3, 1, 0)) && cells.contains(new BlockCoord(-6, 1, 6)),
                "В сетке обязаны быть клетки на шаге 3 и на краю радиуса: иначе шаг или радиус не соблюдён");
        for (BlockCoord cell : cells) {
            int dx = cell.x() - center.x();
            int dz = cell.z() - center.z();
            assertTrue(
                    dx % DATA.areaStep() == 0 && dz % DATA.areaStep() == 0,
                    "Клетка " + cell + " обязана стоять на сетке с шагом " + DATA.areaStep());
            assertTrue(
                    Math.abs(dx) <= DATA.areaRadius() && Math.abs(dz) <= DATA.areaRadius(),
                    "Клетка " + cell + " обязана лежать в радиусе " + DATA.areaRadius());
        }
        int previous = -1;
        for (BlockCoord cell : cells) {
            int distance = cell.x() * cell.x() + cell.z() * cell.z();
            assertTrue(
                    distance >= previous,
                    "Порядок досадки — от ближних клеток к дальним: клетка " + cell + " с расстоянием " + distance
                            + " идёт после " + previous + ", обход недетерминирован");
            previous = distance;
        }
    }

    @Test
    void plan_NoSoilBelow_SkipsCell() {
        TreeGrid grid = flatSoil();
        grid.air(3, 0, 0);
        BlockCoord center = new BlockCoord(0, 1, 0);

        List<BlockCoord> cells = AreaReplant.plan(center, List.of(), DATA, grid);

        assertFalse(
                cells.contains(new BlockCoord(3, 1, 0)),
                "Клетка без почвы под ней обязана пропускаться: саженец на камне не приживётся");
        assertTrue(cells.contains(new BlockCoord(-3, 1, 0)), "Соседняя клетка с почвой обязана остаться в плане");
    }

    @Test
    void plan_BuildingBesideCell_SkipsCellWithinGap() {
        TreeGrid grid = flatSoil();
        grid.planks(3, 1, 1);
        BlockCoord center = new BlockCoord(0, 1, 0);

        List<BlockCoord> cells = AreaReplant.plan(center, List.of(), DATA, grid);

        assertFalse(
                cells.contains(new BlockCoord(3, 1, 0)),
                "Клетка вплотную к постройке (доска в соседней клетке при зазоре 2) обязана пропускаться: "
                        + "саженец не ставится к стене игрока");
        assertTrue(
                cells.contains(new BlockCoord(3, 1, 3)),
                "Клетка дальше зазора от постройки обязана остаться: зазор не запрещает всю досадку");
    }

    @Test
    void plan_OccupiedCell_SkipsCell() {
        TreeGrid grid = flatSoil();
        grid.leaves(3, 1, 0);
        BlockCoord center = new BlockCoord(0, 1, 0);

        List<BlockCoord> cells = AreaReplant.plan(center, List.of(), DATA, grid);

        assertFalse(
                cells.contains(new BlockCoord(3, 1, 0)),
                "Занятая клетка обязана пропускаться: на место листвы или камня саженец не поставишь");
    }

    private static TreeGrid flatSoil() {
        TreeGrid grid = new TreeGrid();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                grid.soil(x, 0, z);
            }
        }
        return grid;
    }
}
