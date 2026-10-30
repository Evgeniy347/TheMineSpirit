package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MineStepStandsTests {
    private static final int LEVEL = 68;
    private static final int FEET = LEVEL + 1;

    @Test
    void of_FirstStepWithSideWalls_StandOnSurfaceOutsideShaft() {
        CellWorld world = ground().set(0, LEVEL, 0, Cell.SOLID);
        for (int y = FEET; y <= FEET + 2; y++) {
            world.set(-1, y, 0, Cell.SOLID).set(1, y, 0, Cell.SOLID);
        }
        MineColumn column = new MineColumn(new BlockCoord(0, LEVEL, 0), MineColumn.PASSAGE_HEIGHT);

        MineStepStands.Choice choice = MineStepStands.of(column, null, new BlockCoord(3, FEET, 0), world);

        assertTrue(choice.found(), "Стенки по бокам входа не отменяют стоянку: дух подходит с поверхности");
        BlockCoord first = choice.stands().get(0);
        assertFalse(
                MineStepStands.shaft(column, first),
                "Первая ступень берётся с поверхности у входа, а не из самого прохода: стоянка " + first
                        + " внутри шахты за стенками — это и есть «стоянка за преградой»");
        assertEquals(
                new BlockCoord(0, FEET, -1),
                first,
                "Ближняя к духу свободная клетка с прямой видимостью — перед входом, стенки по бокам её не прячут");
        assertTrue(MiningSafety.canStand(first, world), "Выбранная стоянка обязана быть безопасной");
    }

    @Test
    void of_BlockAboveShaftStand_StandAtSurfaceBeside() {
        CellWorld world = ground().set(0, LEVEL, 0, Cell.SOLID).set(0, FEET + 1, 0, Cell.SOLID);
        MineColumn column = new MineColumn(new BlockCoord(0, LEVEL, 0), MineColumn.PASSAGE_HEIGHT);

        MineStepStands.Choice choice = MineStepStands.of(column, null, new BlockCoord(3, FEET, 0), world);

        assertTrue(choice.found(), "Блок над клеткой прохода не оставляет духа без стоянки: он встаёт рядом");
        assertNotEquals(
                column.stand(),
                choice.stands().get(0),
                "Клетка прохода с блоком над головой не годится: дух встаёт на поверхность рядом с входом");
        assertTrue(
                MiningSafety.canStand(choice.stands().get(0), world),
                "Стоянка рядом с входом обязана иметь пол и место над головой");
    }

    @Test
    void of_LaterStep_ParentStepComesFirst() {
        CellWorld world = ground().set(0, LEVEL, 0, Cell.SOLID).set(0, LEVEL, 1, Cell.SOLID);
        world.set(0, FEET - 1, 1, Cell.PASSABLE).set(0, LEVEL - 1, 1, Cell.PASSABLE);
        BlockCoord parent = new BlockCoord(0, FEET, 0);
        MineColumn column = new MineColumn(new BlockCoord(0, LEVEL - 2, 1), MineColumn.PASSAGE_HEIGHT);

        MineStepStands.Choice choice = MineStepStands.of(column, parent, new BlockCoord(0, FEET, -2), world);

        assertTrue(choice.found(), "Ступень ниже открывается со ступени выше: стоянка обязана найтись");
        assertEquals(
                parent,
                choice.stands().get(0),
                "Дальше первая ступень берётся со ступени выше родителя, а не с поверхности: " + choice.stands());
        for (BlockCoord stand : choice.stands()) {
            assertFalse(
                    MineStepStands.shaft(column, stand),
                    "Стоянка не может быть внутри самой ступени: там ещё порода, дух не встанет");
        }
    }

    @Test
    void of_ParentStandBehindTerraceWall_StandBehindWallIsNotOffered() {
        BlockCoord from = new BlockCoord(3, FEET, 0);
        BlockCoord parent = new BlockCoord(0, LEVEL + 5, -1);
        MineColumn column = new MineColumn(new BlockCoord(0, LEVEL, 0), MineColumn.PASSAGE_HEIGHT);
        CellWorld world = terraceWall();

        MineStepStands.Choice choice = MineStepStands.of(column, parent, from, world);

        assertTrue(
                choice.found(),
                "Стоянка за стеной не отменяет спуск: дух обязан найти достижимую клетку с прямой видимостью "
                        + "на ступень " + column.stand() + ", иначе проходка встаёт (observe4: (-189,69,-518))");
        assertFalse(
                choice.stands().contains(parent),
                "Стоянка родителя " + parent + " за преградой не предлагается: подход к ней кончается у духа "
                        + "(observe4: «стоянка (-187,69,-517) за преградой»), очередь стоянок из-за неё не тратится");
        for (BlockCoord stand : choice.stands()) {
            assertTrue(
                    MineReach.canReach(from, stand, world),
                    "Каждая предложенная стоянка обязана быть достижима пешком от духа " + from + ": " + stand
                            + " за преградой даёт провал подхода, а не спуск");
            assertTrue(
                    MineStepStands.visibleFrom(column, stand, world),
                    "Каждая предложенная стоянка обязана видеть ступень " + column.stand()
                            + " напрямую: ломать сквозь блоки нельзя, из-за стены клетку не открыть — " + stand);
        }
        assertEquals(
                new BlockCoord(4, LEVEL + 3, 1),
                choice.stands().get(0),
                "Ближняя достижимая клетка с прямой видимостью — на уступе у стены: она открывает верх ступени "
                        + "из-за стены, дух подходит к ней, а не стоит под стеной: " + choice.stands());
    }

    private static CellWorld terraceWall() {
        CellWorld world = new CellWorld()
                .fill(LEVEL, -8, -8, 8, 8, Cell.SOLID)
                .fill(LEVEL + 1, 2, -8, 2, 8, Cell.SOLID)
                .fill(LEVEL + 2, 2, -8, 2, 8, Cell.SOLID)
                .fill(LEVEL + 3, 2, -8, 2, 8, Cell.SOLID);
        world.set(4, LEVEL + 1, 0, Cell.SOLID);
        world.set(4, LEVEL + 2, 1, Cell.SOLID);
        world.set(0, LEVEL + 4, -1, Cell.SOLID);
        world.set(-1, LEVEL + 1, 0, Cell.SOLID).set(1, LEVEL + 1, 0, Cell.SOLID).set(0, LEVEL + 1, 1, Cell.SOLID);
        return world;
    }

    @Test
    void of_EntranceSealed_EmptyWithClearReason() {
        CellWorld world = ground().set(0, LEVEL, 0, Cell.SOLID);
        for (int y = FEET; y <= FEET + 2; y++) {
            world.set(-1, y, 0, Cell.SOLID)
                    .set(1, y, 0, Cell.SOLID)
                    .set(0, y, -1, Cell.SOLID)
                    .set(0, y, 1, Cell.SOLID);
        }
        MineColumn column = new MineColumn(new BlockCoord(0, LEVEL, 0), MineColumn.PASSAGE_HEIGHT);

        MineStepStands.Choice choice = MineStepStands.of(column, null, new BlockCoord(3, FEET, 0), world);

        assertFalse(choice.found(), "Вход замурован со всех сторон: стоянки нет, дух не идёт сквозь блоки");
        assertTrue(
                choice.reason().contains("вход"),
                "Причина отказа обязана называть вход, а не только «нет стоянки»: " + choice.reason());
        assertTrue(
                choice.reason().contains(column.stand().toString()),
                "В причине обязана быть клетка ступени, чтобы в логе было видно, куда дух не подошёл: "
                        + choice.reason());
    }

    @Test
    void visibleFrom_WallBetweenSpiritAndEntrance_False() {
        CellWorld world = ground().set(0, LEVEL, 0, Cell.SOLID);
        MineColumn column = new MineColumn(new BlockCoord(0, LEVEL, 0), MineColumn.PASSAGE_HEIGHT);
        BlockCoord far = new BlockCoord(3, FEET, 0);
        for (int y = LEVEL; y <= FEET + 2; y++) {
            world.set(1, y, 0, Cell.SOLID);
        }

        assertFalse(
                MineStepStands.visibleFrom(column, far, world),
                "Стена между духом и входом закрывает ступень: ломать её сквозь блоки нельзя");
        world.set(1, FEET, 0, Cell.PASSABLE).set(1, FEET + 1, 0, Cell.PASSABLE);
        assertTrue(
                MineStepStands.visibleFrom(column, new BlockCoord(2, FEET, 0), world),
                "Через снятую преграду верх ступени видно: преграду снимают в прямой видимости, не сквозь блоки");
    }

    @Test
    void digOrder_BlockedTop_RemovesVisibleCellFirst() {
        CellWorld world = ground().set(0, LEVEL, 0, Cell.SOLID);
        world.set(0, FEET, 0, Cell.SOLID).set(0, FEET + 1, 0, Cell.SOLID).set(0, FEET + 2, 0, Cell.SOLID);
        MineColumn column = new MineColumn(new BlockCoord(0, LEVEL, 0), MineColumn.PASSAGE_HEIGHT);
        BlockCoord stand = new BlockCoord(0, FEET, -1);

        List<BlockCoord> order = MineDigOrder.column(column, List.of(), stand, world);

        assertEquals(
                new BlockCoord(0, FEET + 2, 0),
                order.get(0),
                "Заваленный вход снимают сверху вниз: первым ломается верхний блок, видимый с поверхности");
        List<BlockCoord> opened = new ArrayList<>();
        for (BlockCoord cell : order) {
            if (column.contains(cell)) {
                assertTrue(
                        MineDigOrder.visible(stand, cell, opened, world),
                        "Клетку шахты ломают только в прямой видимости: " + cell + " при открытых " + opened);
            }
            opened.add(cell);
        }
    }

    private static CellWorld ground() {
        return new CellWorld().fill(LEVEL, -4, -4, 4, 4, Cell.SOLID);
    }
}
