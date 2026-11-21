package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.api.Cell;
import com.theminespirit.core.mining.api.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.api.DropPolicy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MineStepStandsTests {
    static final int LEVEL = 68;
    static final int FEET = LEVEL + 1;

    @Test
    void of_FirstStepWithSideWalls_StandOnSurfaceOutsideShaft() {
        CellWorld world = ground().set(0, LEVEL, 0, Cell.SOLID);
        for (int y = FEET; y <= FEET + 2; y++) {
            world.set(-1, y, 0, Cell.SOLID).set(1, y, 0, Cell.SOLID);
        }
        MineColumn column = new MineColumn(new BlockCoord(0, LEVEL, 0), MineColumn.PASSAGE_HEIGHT);

        MineStepStands.Choice choice = MineStepStands.of(column, null, new BlockCoord(3, FEET, 0), world);

        assertTrue(
                choice.found(),
                "Стенки по бокам входа не отменяют стоянку: дух подходит с поверхности"
                        + " Иначе тест of_FirstStepWithSideWalls_StandOnSurfaceOutsideShaft перестанет ловить эту поломку. При смене of_FirstStepWithSideWalls_StandOnSurfaceOutsideShaft проверь это утверждение.");
        BlockCoord first = choice.stands().get(0);
        assertFalse(
                MineStepStands.shaft(column, first),
                "Первая ступень берётся с поверхности у входа, а не из самого прохода: стоянка " + first
                        + " внутри шахты за стенками — это и есть «стоянка за преградой»"
                        + " Иначе тест of_FirstStepWithSideWalls_StandOnSurfaceOutsideShaft перестанет ловить эту поломку. При смене of_FirstStepWithSideWalls_StandOnSurfaceOutsideShaft проверь это утверждение.");
        assertEquals(
                new BlockCoord(0, FEET, -1),
                first,
                "Ближняя к духу свободная клетка с прямой видимостью — перед входом, стенки по бокам её не прячут"
                        + " Иначе тест of_FirstStepWithSideWalls_StandOnSurfaceOutsideShaft перестанет ловить эту поломку. При смене of_FirstStepWithSideWalls_StandOnSurfaceOutsideShaft проверь это утверждение.");
        assertTrue(
                MiningSafety.canStand(first, world),
                "Выбранная стоянка обязана быть безопасной"
                        + " Иначе тест of_FirstStepWithSideWalls_StandOnSurfaceOutsideShaft перестанет ловить эту поломку. При смене of_FirstStepWithSideWalls_StandOnSurfaceOutsideShaft проверь это утверждение.");
    }

    @Test
    void of_BlockAboveShaftStand_StandAtSurfaceBeside() {
        CellWorld world = ground().set(0, LEVEL, 0, Cell.SOLID).set(0, FEET + 1, 0, Cell.SOLID);
        MineColumn column = new MineColumn(new BlockCoord(0, LEVEL, 0), MineColumn.PASSAGE_HEIGHT);

        MineStepStands.Choice choice = MineStepStands.of(column, null, new BlockCoord(3, FEET, 0), world);

        assertTrue(
                choice.found(),
                "Блок над клеткой прохода не оставляет духа без стоянки: он встаёт рядом"
                        + " Иначе тест of_BlockAboveShaftStand_StandAtSurfaceBeside перестанет ловить эту поломку. При смене of_BlockAboveShaftStand_StandAtSurfaceBeside проверь это утверждение.");
        assertNotEquals(
                column.stand(),
                choice.stands().get(0),
                "Клетка прохода с блоком над головой не годится: дух встаёт на поверхность рядом с входом"
                        + " Иначе тест of_BlockAboveShaftStand_StandAtSurfaceBeside перестанет ловить эту поломку. При смене of_BlockAboveShaftStand_StandAtSurfaceBeside проверь это утверждение.");
        assertTrue(
                MiningSafety.canStand(choice.stands().get(0), world),
                "Стоянка рядом с входом обязана иметь пол и место над головой"
                        + " Иначе тест of_BlockAboveShaftStand_StandAtSurfaceBeside перестанет ловить эту поломку. При смене of_BlockAboveShaftStand_StandAtSurfaceBeside проверь это утверждение.");
    }

    @Test
    void of_LaterStep_ParentStepComesFirst() {
        CellWorld world = ground().set(0, LEVEL, 0, Cell.SOLID).set(0, LEVEL, 1, Cell.SOLID);
        world.set(0, FEET - 1, 1, Cell.PASSABLE).set(0, LEVEL - 1, 1, Cell.PASSABLE);
        BlockCoord parent = new BlockCoord(0, FEET, 0);
        MineColumn column = new MineColumn(new BlockCoord(0, LEVEL - 2, 1), MineColumn.PASSAGE_HEIGHT);

        MineStepStands.Choice choice = MineStepStands.of(column, parent, new BlockCoord(0, FEET, -2), world);

        assertTrue(
                choice.found(),
                "Ступень ниже открывается со ступени выше: стоянка обязана найтись"
                        + " Иначе тест of_LaterStep_ParentStepComesFirst перестанет ловить эту поломку. При смене of_LaterStep_ParentStepComesFirst проверь это утверждение.");
        assertEquals(
                parent,
                choice.stands().get(0),
                "Дальше первая ступень берётся со ступени выше родителя, а не с поверхности: " + choice.stands()
                        + " Иначе тест of_LaterStep_ParentStepComesFirst перестанет ловить эту поломку. При смене of_LaterStep_ParentStepComesFirst проверь это утверждение.");
        for (BlockCoord stand : choice.stands()) {
            assertFalse(
                    MineStepStands.shaft(column, stand),
                    "Стоянка не может быть внутри самой ступени: там ещё порода, дух не встанет"
                            + " Иначе тест of_LaterStep_ParentStepComesFirst перестанет ловить эту поломку. При смене of_LaterStep_ParentStepComesFirst проверь это утверждение.");
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
                        + "на ступень " + column.stand() + ", иначе проходка встаёт (observe4: (-189,69,-518))"
                        + " При смене of_ParentStandBehindTerraceWall_StandBehindWallIsNotOffered проверь это утверждение.");
        assertFalse(
                choice.stands().contains(parent),
                "Стоянка родителя " + parent + " за преградой не предлагается: подход к ней кончается у духа "
                        + "(observe4: «стоянка (-187,69,-517) за преградой»), очередь стоянок из-за неё не тратится"
                        + " Иначе тест of_ParentStandBehindTerraceWall_StandBehindWallIsNotOffered перестанет ловить эту поломку. При смене of_ParentStandBehindTerraceWall_StandBehindWallIsNotOffered проверь это утверждение.");
        for (BlockCoord stand : choice.stands()) {
            assertTrue(
                    MineReach.canReach(from, stand, world),
                    "Каждая предложенная стоянка обязана быть достижима пешком от духа " + from + ": " + stand
                            + " за преградой даёт провал подхода, а не спуск"
                            + " Иначе тест of_ParentStandBehindTerraceWall_StandBehindWallIsNotOffered перестанет ловить эту поломку. При смене of_ParentStandBehindTerraceWall_StandBehindWallIsNotOffered проверь это утверждение.");
            assertTrue(
                    MineStepStands.visibleFrom(column, stand, world),
                    "Каждая предложенная стоянка обязана видеть ступень " + column.stand()
                            + " напрямую: ломать сквозь блоки нельзя, из-за стены клетку не открыть — " + stand
                            + " Иначе тест of_ParentStandBehindTerraceWall_StandBehindWallIsNotOffered перестанет ловить эту поломку. При смене of_ParentStandBehindTerraceWall_StandBehindWallIsNotOffered проверь это утверждение.");
        }
        assertEquals(
                new BlockCoord(4, LEVEL + 3, 1),
                choice.stands().get(0),
                "Ближняя достижимая клетка с прямой видимостью — на уступе у стены: она открывает верх ступени "
                        + "из-за стены, дух подходит к ней, а не стоит под стеной: " + choice.stands()
                        + " Иначе тест of_ParentStandBehindTerraceWall_StandBehindWallIsNotOffered перестанет ловить эту поломку. При смене of_ParentStandBehindTerraceWall_StandBehindWallIsNotOffered проверь это утверждение.");
    }

    static CellWorld terraceWall() {
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

        assertFalse(
                choice.found(),
                "Вход замурован со всех сторон: стоянки нет, дух не идёт сквозь блоки"
                        + " Иначе тест of_EntranceSealed_EmptyWithClearReason перестанет ловить эту поломку. При смене of_EntranceSealed_EmptyWithClearReason проверь это утверждение.");
        assertTrue(
                choice.reason().contains("вход"),
                "Причина отказа обязана называть вход, а не только «нет стоянки»: " + choice.reason()
                        + " Иначе тест of_EntranceSealed_EmptyWithClearReason перестанет ловить эту поломку. При смене of_EntranceSealed_EmptyWithClearReason проверь это утверждение.");
        assertTrue(
                choice.reason().contains(column.stand().toString()),
                "В причине обязана быть клетка ступени, чтобы в логе было видно, куда дух не подошёл: "
                        + choice.reason()
                        + " Иначе тест of_EntranceSealed_EmptyWithClearReason перестанет ловить эту поломку. При смене of_EntranceSealed_EmptyWithClearReason проверь это утверждение.");
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
                "Стена между духом и входом закрывает ступень: ломать её сквозь блоки нельзя"
                        + " Иначе тест visibleFrom_WallBetweenSpiritAndEntrance_False перестанет ловить эту поломку. При смене visibleFrom_WallBetweenSpiritAndEntrance_False проверь это утверждение.");
        world.set(1, FEET, 0, Cell.PASSABLE).set(1, FEET + 1, 0, Cell.PASSABLE);
        assertTrue(
                MineStepStands.visibleFrom(column, new BlockCoord(2, FEET, 0), world),
                "Через снятую преграду верх ступени видно: преграду снимают в прямой видимости, не сквозь блоки"
                        + " Иначе тест visibleFrom_WallBetweenSpiritAndEntrance_False перестанет ловить эту поломку. При смене visibleFrom_WallBetweenSpiritAndEntrance_False проверь это утверждение.");
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
                "Заваленный вход снимают сверху вниз: первым ломается верхний блок, видимый с поверхности"
                        + " При смене digOrder_BlockedTop_RemovesVisibleCellFirst проверь это утверждение.");
        List<BlockCoord> opened = new ArrayList<>();
        for (BlockCoord cell : order) {
            if (column.contains(cell)) {
                assertTrue(
                        MineDigOrder.visible(stand, cell, opened, world),
                        "Клетку шахты ломают только в прямой видимости: " + cell + " при открытых " + opened
                                + " Иначе тест digOrder_BlockedTop_RemovesVisibleCellFirst перестанет ловить эту поломку. При смене digOrder_BlockedTop_RemovesVisibleCellFirst проверь это утверждение.");
            }
            opened.add(cell);
        }
    }

    @Test
    void of_LiveObserve6PitBehindWall_NotOffered() {
        BlockCoord from = new BlockCoord(-191, FEET, -517);
        MineColumn column = new MineColumn(new BlockCoord(-189, LEVEL, -518), MineColumn.PASSAGE_HEIGHT);
        CellWorld world = Observe6MineWorld.build();

        MineStepStands.Choice choice = MineStepStands.of(column, null, from, world);

        BlockCoord trap = new BlockCoord(-189, LEVEL, -517);
        assertTrue(
                choice.found(),
                "Живая шахта observe6: к ступени " + column.stand()
                        + " обязана найтись стоянка, иначе проходка встаёт на 75% (5 провалов → BLOCKED): "
                        + choice.reason()
                        + " При смене of_LiveObserve6PitBehindWall_NotOffered проверь это утверждение.");
        assertFalse(
                choice.stands().contains(trap),
                "Стоянка " + trap + " достижима только падением на 3 блока в тупиковую яму, а навигация духа "
                        + "роняет не глубже " + DropPolicy.MAX_DROP
                        + ": её выбор и дал «путь упёрся в тупик, стоянка (-191,69,-517) за преградой» (observe6)"
                        + " Иначе тест of_LiveObserve6PitBehindWall_NotOffered перестанет ловить эту поломку. При смене of_LiveObserve6PitBehindWall_NotOffered проверь это утверждение.");
        for (BlockCoord stand : choice.stands()) {
            assertTrue(
                    MineStepStands.visibleFrom(column, stand, world),
                    "Каждая предложенная стоянка обязана видеть ступень " + column.stand() + ": " + stand
                            + " Иначе тест of_LiveObserve6PitBehindWall_NotOffered перестанет ловить эту поломку. При смене of_LiveObserve6PitBehindWall_NotOffered проверь это утверждение.");
        }
    }

    static CellWorld ground() {
        return new CellWorld().fill(LEVEL, -4, -4, 4, 4, Cell.SOLID);
    }
}
