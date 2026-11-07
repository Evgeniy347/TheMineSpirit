package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarehouseChestsTests {
    private static final BlockCoord ORIGIN = new BlockCoord(100, 64, -50);

    @Test
    void cells_EveryLevel_MatchesDoubleChestPlan() {
        int[] doubles = {9, 27, 81, 243};
        for (int level = 1; level <= WarehouseRules.MAX_LEVEL; level++) {
            List<BlockCoord> cells = WarehouseChests.cells(ORIGIN, level);

            assertEquals(
                    doubles[level - 1] * 2,
                    cells.size(),
                    "Склад уровня " + level + " — " + doubles[level - 1]
                            + " двойных сундуков: клеток ровно вдвое больше");
        }
    }

    @Test
    void cells_LevelOne_AreShiftedFromOrigin() {
        List<BlockCoord> cells = WarehouseChests.cells(ORIGIN, 1);

        assertTrue(
                cells.stream().allMatch(cell -> cell.y() > ORIGIN.y()),
                "Сундуки выше пола склада: пол на уровне origin, сундуки на следующем");
        assertTrue(
                cells.stream().allMatch(cell -> cell.y() > ORIGIN.y() && cell.y() <= ORIGIN.y() + 3),
                "Сундуки storage_v1 стоят на трёх уровнях от пола: "
                        + cells.stream().limit(4).toList());
        assertTrue(
                cells.stream()
                        .allMatch(cell -> cell.x() >= ORIGIN.x()
                                && cell.x() < ORIGIN.x() + 6
                                && cell.z() >= ORIGIN.z()
                                && cell.z() < ORIGIN.z() + 7),
                "Сундуки не выходят за габариты схемы 6×7");
    }
}
