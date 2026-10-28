package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MineDigOrderTests {
    @Test
    void from_ThreeHighShaft_OrdersTopDown() {
        CellWorld world = shaft();
        BlockCoord stand = new BlockCoord(0, 64, 0);
        List<BlockCoord> cells = List.of(new BlockCoord(1, 64, 0), new BlockCoord(1, 65, 0), new BlockCoord(1, 66, 0));

        assertEquals(
                List.of(new BlockCoord(1, 66, 0), new BlockCoord(1, 65, 0), new BlockCoord(1, 64, 0)),
                MineDigOrder.from(stand, cells, world),
                "Спуск 3×N рубится сверху вниз: верхняя клетка открывает вид на следующую, нижняя за ней скрыта");
    }

    @Test
    void from_WallHidesNext_OpensNearFirst() {
        CellWorld world = new CellWorld().set(0, -1, 0, Cell.SOLID);
        for (int x = 1; x <= 2; x++) {
            world.set(x, 0, 0, Cell.SOLID)
                    .set(x, 1, 0, Cell.SOLID)
                    .set(x, 2, 0, Cell.SOLID)
                    .set(x, 1, 1, Cell.SOLID)
                    .set(x, 1, -1, Cell.SOLID);
        }
        world.set(3, 1, 0, Cell.SOLID);
        BlockCoord stand = new BlockCoord(0, 0, 0);
        List<BlockCoord> cells = List.of(new BlockCoord(1, 1, 0), new BlockCoord(2, 1, 0));

        List<BlockCoord> order = MineDigOrder.from(stand, cells, world);

        assertEquals(
                cells,
                order,
                "Ближняя клетка стены открывает дальнюю: порядок обязан идти от видимой к скрытой, а не наоборот");
        List<BlockCoord> opened = new ArrayList<>();
        for (BlockCoord cell : order) {
            assertTrue(
                    MineDigOrder.visible(stand, cell, opened, world),
                    "Каждая следующая клетка обязана быть видна с стоянки после снятия предыдущих: " + cell
                            + " при открытых " + opened);
            opened.add(cell);
        }
    }

    @Test
    void column_ThreeHighShaft_StartsWithTopCell() {
        CellWorld world = shaft();
        MineColumn column = new MineColumn(new BlockCoord(1, 63, 0), 3);

        List<BlockCoord> order = MineDigOrder.column(column, List.of(), new BlockCoord(0, 64, 0), world);

        assertEquals(
                new BlockCoord(1, 66, 0),
                order.get(0),
                "Проходка колонки начинается с верхней клетки прохода: она видна из старой стоянки, нижние скрыты");
    }

    @Test
    void from_WidenedStep_OrdersTopLaneFirst() {
        List<BlockCoord> cells = new ArrayList<>();
        for (int lane = -1; lane <= 1; lane++) {
            for (int above = 1; above <= 2; above++) {
                cells.add(new BlockCoord(1, 64 + above, lane));
            }
        }

        List<BlockCoord> order = MineDigOrder.from(new BlockCoord(0, 64, 0), cells, new CellWorld());

        assertTrue(
                order.get(0).y() == 66 && order.get(1).y() == 66 && order.get(2).y() == 66,
                "Расширение ступени шириной 3 сначала снимает верхний ряд: нижний ряд виден только через него, "
                        + "порядок " + order);
    }

    private static CellWorld shaft() {
        CellWorld world = new CellWorld()
                .set(0, 63, 0, Cell.SOLID)
                .set(0, 64, 0, Cell.PASSABLE)
                .set(0, 65, 0, Cell.PASSABLE)
                .set(0, 66, 0, Cell.PASSABLE);
        for (int x = 1; x <= 2; x++) {
            world.set(x, 63, 0, Cell.SOLID);
        }
        return world;
    }
}
