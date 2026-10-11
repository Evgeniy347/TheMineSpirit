package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class StandSpotsTests {
    @Test
    void of_TargetWithOpenSide_ReturnsAdjacentStand() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.SOLID).set(1, -1, 0, Cell.SOLID);

        List<BlockCoord> spots = StandSpots.of(new BlockCoord(0, 0, 0), world, new BlockCoord(0, 0, 0));

        assertEquals(List.of(new BlockCoord(1, 0, 0)), spots,
                "Обнажённая сбоку руда даёт стоянку в проходе: без неё дух не подходит к цели и стоит");
    }

    @Test
    void of_SealedTarget_IsEmpty() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(0, 1, 0, Cell.SOLID).set(0, -1, 0, Cell.SOLID)
                .set(1, 0, 0, Cell.SOLID).set(-1, 0, 0, Cell.SOLID)
                .set(0, 0, 1, Cell.SOLID).set(0, 0, -1, Cell.SOLID);

        assertEquals(List.of(), StandSpots.of(new BlockCoord(0, 0, 0), world, new BlockCoord(0, 0, 0)),
                "Полностью замурованная руда не даёт стоянок: дух обязан пропустить её, а не пробивать стену");
    }

    @Test
    void of_StandAtCliffWithoutFloor_IsRejected() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(0, 1, 0, Cell.SOLID).set(0, -1, 0, Cell.SOLID)
                .set(-1, 0, 0, Cell.SOLID).set(0, 0, 1, Cell.SOLID).set(0, 0, -1, Cell.SOLID);

        assertEquals(List.of(), StandSpots.of(new BlockCoord(0, 0, 0), world, new BlockCoord(0, 0, 0)),
                "Единственный проход — обрыв без пола: стоянка отвергается, иначе дух падает в яму");
    }

    @Test
    void of_StandNextToLava_IsRejected() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(0, 1, 0, Cell.SOLID).set(0, -1, 0, Cell.SOLID)
                .set(-1, 0, 0, Cell.SOLID).set(0, 0, 1, Cell.SOLID).set(0, 0, -1, Cell.SOLID)
                .set(1, -1, 0, Cell.SOLID)
                .set(1, 0, 1, Cell.LAVA);

        assertEquals(List.of(), StandSpots.of(new BlockCoord(0, 0, 0), world, new BlockCoord(0, 0, 0)),
                "Стоянка рядом с лавой отвергается: дух иначе сгорит, стоя у цели");
    }

    @Test
    void of_TargetOnCeiling_ReturnsFloorStandBelow() {
        CellWorld world = new CellWorld()
                .fill(10, -2, -2, 2, 2, Cell.SOLID)
                .fill(14, -2, -2, 2, 2, Cell.SOLID);

        List<BlockCoord> spots = StandSpots.of(new BlockCoord(0, 14, 0), world, new BlockCoord(0, 0, 0));

        assertEquals(List.of(new BlockCoord(0, 11, 0)), spots,
                "Руда на потолке достаётся с клетки пола внизу: без этого подпотолочные руды недосягаемы");
    }

    @Test
    void of_TwoOpenSides_SpotsSortedByDistanceFrom() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(0, 1, 0, Cell.SOLID).set(0, -1, 0, Cell.SOLID)
                .set(0, 0, 1, Cell.SOLID).set(0, 0, -1, Cell.SOLID)
                .set(1, -1, 0, Cell.SOLID).set(-1, -1, 0, Cell.SOLID);

        List<BlockCoord> spots = StandSpots.of(new BlockCoord(0, 0, 0), world, new BlockCoord(5, 0, 0));

        assertEquals(List.of(new BlockCoord(1, 0, 0), new BlockCoord(-1, 0, 0)), spots,
                "Стоянки сортируются по расстоянию от духа: ближняя стоянка первой сокращает путь");
    }

    @Test
    void of_TargetReading_StaysWithinLimit() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(0, 1, 0, Cell.PASSABLE).set(0, -1, 0, Cell.PASSABLE)
                .set(1, 0, 0, Cell.PASSABLE).set(-1, 0, 0, Cell.PASSABLE)
                .set(0, 0, 1, Cell.PASSABLE).set(0, 0, -1, Cell.PASSABLE);

        StandSpots.of(new BlockCoord(0, 0, 0), world, new BlockCoord(0, 0, 0));

        assertTrue(world.reads() <= StandSpots.MAX_VIEW_READS,
                "Поиск стоянок читает не более " + StandSpots.MAX_VIEW_READS + " клеток. Чтений: " + world.reads());
    }
}
