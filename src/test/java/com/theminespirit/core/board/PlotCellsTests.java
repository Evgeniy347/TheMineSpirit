package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PlotCellsTests {

    @Test
    void defect_HoleInGroundLayer_Restored() {
        assertEquals(
                Optional.of(PlotCells.Defect.HOLE),
                PlotCells.defect(new PlotCells.Cell(-1, false, false), PlotCells.Found.OPEN),
                "Дыра в земле участка под уровнем ног — восстановление подсыпкой: в неё падали духи (INC-73)");
    }

    @Test
    void defect_NaturalBlockOnPlot_Cut() {
        assertEquals(
                Optional.of(PlotCells.Defect.EXTRA),
                PlotCells.defect(new PlotCells.Cell(0, false, false), PlotCells.Found.NATURAL),
                "Лишний природный блок над уровнем участка — срезка: участок остаётся ровным");
    }

    @Test
    void defect_HutFloorInsteadOfGround_Fine() {
        assertEquals(
                Optional.empty(),
                PlotCells.defect(new PlotCells.Cell(-1, false, true), PlotCells.Found.OTHER),
                "Пол хижины на месте земли — постройка плана, не дыра: восстановление не ломает постройки");
    }

    @Test
    void defect_MineEntranceOpen_Untouched() {
        assertEquals(
                Optional.empty(),
                PlotCells.defect(new PlotCells.Cell(-1, true, false), PlotCells.Found.OPEN),
                "Вход и спуск в шахту открыты по плану — не заделываются, иначе шахту засыпали бы по кругу");
    }

    @Test
    void expect_CellsByHeight_GroundFreeAny() {
        assertEquals(
                List.of(
                        PlotCells.Expect.ANY,
                        PlotCells.Expect.GROUND,
                        PlotCells.Expect.FREE,
                        PlotCells.Expect.FREE,
                        PlotCells.Expect.ANY),
                IntStream.of(-2, -1, 0, PlotCells.FREE_HEIGHT - 1, PlotCells.FREE_HEIGHT)
                        .mapToObj(dy -> PlotCells.expect(new PlotCells.Cell(dy, false, false)))
                        .toList(),
                "Земля — слой под ногами участка, свободно — " + PlotCells.FREE_HEIGHT + " блока над ним; глубже и "
                        + "выше аудит ровности не судит");
    }
}
