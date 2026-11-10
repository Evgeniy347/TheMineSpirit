package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StandSpotsTests {
    @Test
    void above_SealedTargetWithFreeTop_ReturnsTopStand() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(0, 1, 0, Cell.PASSABLE)
                .set(0, 2, 0, Cell.PASSABLE)
                .set(1, 0, 0, Cell.SOLID)
                .set(-1, 0, 0, Cell.SOLID)
                .set(0, 0, 1, Cell.SOLID)
                .set(0, 0, -1, Cell.SOLID);

        assertEquals(
                Optional.of(new BlockCoord(0, 1, 0)),
                StandSpots.above(new BlockCoord(0, 0, 0), world),
                "Полностью замурованная клетка с открытым верхом даёт стоянку сверху: с неё дух срывает бугор "
                        + "и ставит блок, а не падает пять раз на «стоянки недостижимы»");
    }

    @Test
    void above_TargetWithoutSolidTop_IsEmpty() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.PASSABLE).set(0, 1, 0, Cell.PASSABLE);

        assertEquals(
                Optional.empty(),
                StandSpots.above(new BlockCoord(0, 0, 0), world),
                "Стоянка сверху допустима только на твёрдой цели: над воздухом дух не стоит");
    }

    @Test
    void of_TargetWithOpenSide_ReturnsAdjacentStand() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.SOLID).set(1, -1, 0, Cell.SOLID);

        List<BlockCoord> spots = StandSpots.of(new BlockCoord(0, 0, 0), world, new BlockCoord(0, 0, 0));

        assertEquals(
                List.of(new BlockCoord(1, 0, 0)),
                spots,
                "Обнажённая сбоку руда даёт стоянку в проходе: без неё дух не подходит к цели и стоит");
    }

    @Test
    void of_SealedTarget_IsEmpty() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(0, 1, 0, Cell.SOLID)
                .set(0, -1, 0, Cell.SOLID)
                .set(1, 0, 0, Cell.SOLID)
                .set(-1, 0, 0, Cell.SOLID)
                .set(0, 0, 1, Cell.SOLID)
                .set(0, 0, -1, Cell.SOLID);

        assertEquals(
                List.of(),
                StandSpots.of(new BlockCoord(0, 0, 0), world, new BlockCoord(0, 0, 0)),
                "Полностью замурованная руда не даёт стоянок: дух обязан пропустить её, а не пробивать стену");
    }

    @Test
    void of_StandAtCliffWithoutFloor_IsRejected() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(0, 1, 0, Cell.SOLID)
                .set(0, -1, 0, Cell.SOLID)
                .set(-1, 0, 0, Cell.SOLID)
                .set(0, 0, 1, Cell.SOLID)
                .set(0, 0, -1, Cell.SOLID);

        assertEquals(
                List.of(),
                StandSpots.of(new BlockCoord(0, 0, 0), world, new BlockCoord(0, 0, 0)),
                "Единственный проход — обрыв без пола: стоянка отвергается, иначе дух падает в яму");
    }

    @Test
    void of_StandNextToLava_IsRejected() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(0, 1, 0, Cell.SOLID)
                .set(0, -1, 0, Cell.SOLID)
                .set(-1, 0, 0, Cell.SOLID)
                .set(0, 0, 1, Cell.SOLID)
                .set(0, 0, -1, Cell.SOLID)
                .set(1, -1, 0, Cell.SOLID)
                .set(1, 0, 1, Cell.LAVA);

        assertEquals(
                List.of(),
                StandSpots.of(new BlockCoord(0, 0, 0), world, new BlockCoord(0, 0, 0)),
                "Стоянка рядом с лавой отвергается: дух иначе сгорит, стоя у цели");
    }

    @Test
    void of_TargetOnCeiling_ReturnsFloorStandBelow() {
        CellWorld world = new CellWorld()
                .fill(10, -2, -2, 2, 2, Cell.SOLID)
                .fill(14, -2, -2, 2, 2, Cell.SOLID)
                .set(0, 15, 0, Cell.SOLID);

        List<BlockCoord> spots = StandSpots.of(new BlockCoord(0, 14, 0), world, new BlockCoord(0, 0, 0));

        assertEquals(
                List.of(new BlockCoord(0, 11, 0)),
                spots,
                "Руда на потолке достаётся с клетки пола внизу: без этого подпотолочные руды недосягаемы");
    }

    @Test
    void of_TwoOpenSides_SpotsSortedByDistanceFrom() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(0, 1, 0, Cell.SOLID)
                .set(0, -1, 0, Cell.SOLID)
                .set(0, 0, 1, Cell.SOLID)
                .set(0, 0, -1, Cell.SOLID)
                .set(1, -1, 0, Cell.SOLID)
                .set(-1, -1, 0, Cell.SOLID);

        List<BlockCoord> spots = StandSpots.of(new BlockCoord(0, 0, 0), world, new BlockCoord(5, 0, 0));

        assertEquals(
                List.of(new BlockCoord(1, 0, 0), new BlockCoord(-1, 0, 0)),
                spots,
                "Стоянки сортируются по расстоянию от духа: ближняя стоянка первой сокращает путь");
    }

    @Test
    void of_FlatGroundTarget_ReturnsStandsBesideTargetAtSurface() {
        CellWorld world = new CellWorld().fill(0, -2, -2, 2, 2, Cell.SOLID);

        List<BlockCoord> spots = StandSpots.of(new BlockCoord(0, 0, 0), world, new BlockCoord(0, 0, 0));

        assertEquals(
                List.of(
                        new BlockCoord(-1, 1, 0),
                        new BlockCoord(0, 1, -1),
                        new BlockCoord(0, 1, 1),
                        new BlockCoord(1, 1, 0)),
                spots,
                "Руда, открытая только сверху на ровной земле, обязана дать стоянки у поверхности рядом с целью, "
                        + "а не остаться недосягаемой: столб над целью исключён («пол=цель»), под целью нет пола");
    }

    @Test
    void of_FallingBlockAboveTarget_RejectsValidStandUnderTarget() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(0, 1, 0, Cell.FALLING)
                .set(0, -3, 0, Cell.SOLID)
                .set(1, 0, 0, Cell.PASSABLE)
                .set(1, -1, 0, Cell.SOLID);

        List<BlockCoord> spots = StandSpots.of(new BlockCoord(0, 0, 0), world, new BlockCoord(0, 0, 0));

        assertEquals(
                List.of(new BlockCoord(1, 0, 0)),
                spots,
                "Стоянка в столбце руды ниже её под гравием сверху запрещена: после ломки гравий падает сквозь "
                        + "полость на духа; стоянка сбоку от столбца разрешена");
    }

    @Test
    void of_DeepestStandBelowTarget_MaxOffsetIsNine() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.SOLID).set(0, -4, 0, Cell.SOLID);
        BlockCoord target = new BlockCoord(0, 0, 0);

        List<BlockCoord> spots = StandSpots.of(target, world, new BlockCoord(0, 0, 0));

        assertEquals(
                List.of(new BlockCoord(0, -3, 0)),
                spots,
                "Самая глубокая стоянка обязана быть на 3 ниже цели: соседи дают не дальше (1,-2,0)=√5, "
                        + "низ даёт (0,-3,0)=3; иначе ранний выход планировщика вычитает неверный радиус");
        long maxOffset = spots.stream()
                .mapToLong(stand -> (long) (stand.x() - target.x()) * (stand.x() - target.x())
                        + (long) (stand.y() - target.y()) * (stand.y() - target.y())
                        + (long) (stand.z() - target.z()) * (stand.z() - target.z()))
                .max()
                .orElseThrow();
        assertEquals(
                9,
                maxOffset,
                "Наибольшее смещение стоянки обязано равняться 9 = 3² (столб под целью): по нему планировщик "
                        + "вычитает 3 в раннем выходе. Стоянок: " + spots.size());
    }

    @Test
    void of_CeilingOfWaterPocket_IsUnreachable() {
        CellWorld world = new CellWorld()
                .set(0, 10, 0, Cell.SOLID)
                .set(0, 9, 0, Cell.WATER)
                .set(1, 10, 0, Cell.SOLID)
                .set(-1, 10, 0, Cell.SOLID)
                .set(0, 10, 1, Cell.SOLID)
                .set(0, 10, -1, Cell.SOLID)
                .set(0, 11, 0, Cell.SOLID);

        assertEquals(
                List.of(),
                StandSpots.of(new BlockCoord(0, 10, 0), world, new BlockCoord(0, 5, 0)),
                "Клетка на потолке водяного кармана не даёт стоянок (живой случай -103,75,-411): вода под ней "
                        + "не пол, а бока замурованы. Ремонт такой клетки не ставится, а «пути нет» уходит в граф");
    }

    @Test
    void of_TargetReading_StaysWithinLimit() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(0, 1, 0, Cell.PASSABLE)
                .set(0, -1, 0, Cell.PASSABLE)
                .set(1, 0, 0, Cell.PASSABLE)
                .set(-1, 0, 0, Cell.PASSABLE)
                .set(0, 0, 1, Cell.PASSABLE)
                .set(0, 0, -1, Cell.PASSABLE);

        StandSpots.of(new BlockCoord(0, 0, 0), world, new BlockCoord(0, 0, 0));

        assertTrue(world.reads() <= 440, "Поиск стоянок читает не более " + 440 + " клеток. Чтений: " + world.reads());
    }
}
