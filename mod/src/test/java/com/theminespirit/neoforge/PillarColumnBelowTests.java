package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class PillarColumnBelowTests {
    private static final String LIVE =
            "Живой сервер 24.11: дух spirit-4 стоял на брошенном своём столбе из земли (-137, 67..69, -455),"
                    + " расчистка падала «нужен откат аренды» и не разбирала столб: клетки не снимались вовсе";

    @Test
    void columnBelow_LeasedSolidCells_ReturnsTopDown() {
        List<BlockCoord> cells = PillarDownScript.columnBelow(0, 70, 0, 60, cell -> cell.y() >= 67 && cell.y() <= 69);

        assertEquals(
                List.of(new BlockCoord(0, 69, 0), new BlockCoord(0, 68, 0), new BlockCoord(0, 67, 0)),
                cells,
                "Свой столб под духом разбирают сверху вниз, иначе первый же блок заперт верхними. " + LIVE
                        + ". При смене columnBelow_LeasedSolidCells_ReturnsTopDown проверь порядок разбора столба.");
    }

    @Test
    void columnBelow_BrokenOwnCell_SkipsIt() {
        List<BlockCoord> cells = PillarDownScript.columnBelow(0, 70, 0, 60, cell -> cell.y() == 69 || cell.y() == 67);

        assertEquals(
                List.of(new BlockCoord(0, 69, 0), new BlockCoord(0, 67, 0)),
                cells,
                "Уже сломанная своя клетка в список не попадает, иначе разбор ждёт блок, которого нет, и"
                        + " застревает на пустой клетке. При смене columnBelow_BrokenOwnCell_SkipsIt проверь"
                        + " пропуск пустых клеток.");
    }

    @Test
    void columnBelow_NoOwnCells_ReturnsEmpty() {
        List<BlockCoord> cells = PillarDownScript.columnBelow(0, 70, 0, 60, cell -> false);

        assertTrue(
                cells.isEmpty(),
                "Без своих клеток под духом список пуст, иначе расчистка начнёт разбор чужой постройки и снесёт"
                        + " блоки вне перечня задания. При смене columnBelow_NoOwnCells_ReturnsEmpty проверь"
                        + " отказ от разбора.");
    }

    @Test
    void columnBelow_FloorBoundsScan_StopsAboveFloor() {
        List<BlockCoord> cells = PillarDownScript.columnBelow(0, 70, 0, 68, cell -> true);

        assertEquals(
                List.of(new BlockCoord(0, 69, 0), new BlockCoord(0, 68, 0)),
                cells,
                "Ниже предела COUNT_MAX/MIN_BUILD_HEIGHT столб не сканируется, иначе поиск уходит до дна мира и"
                        + " жжёт тики. При смене columnBelow_FloorBoundsScan_StopsAboveFloor проверь границу скана.");
    }
}
