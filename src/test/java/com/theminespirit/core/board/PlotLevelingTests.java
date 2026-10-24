package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlotLevelingTests {
    private static final BlockCoord ANCHOR = new BlockCoord(0, 64, 0);

    @Test
    void isLevel_StepOfOneBlock_Level() {
        assertTrue(
                PlotLeveling.isLevel(ANCHOR, 11, (x, z) -> x > 0 ? 65 : 64),
                "Ступенька в блок — в допуске: ровный участок не выравнивается по кругу");
    }

    @Test
    void isLevel_HumpOfTwoBlocks_NotLevel() {
        assertFalse(
                PlotLeveling.isLevel(ANCHOR, 11, (x, z) -> x == 11 && z == -11 ? 66 : 64),
                "Бугор в два блока у края участка 23×23 — вне допуска: участок без деревьев всё равно выравнивается "
                        + "(INC-50)");
    }

    @Test
    void mineColumn_DescentInsidePlot_SkippedByLeveling() {
        BlockCoord entrance = SettlementLayout.mineEntrance(ANCHOR);

        assertTrue(
                SettlementLayout.mineColumn(ANCHOR, entrance.x(), entrance.z() + 5),
                "Спуск в шахту на юг от входа — не яма участка: подсыпка засыпала бы его");
        assertFalse(
                SettlementLayout.mineColumn(ANCHOR, entrance.x() + 2, entrance.z()),
                "Колонка за стенкой спуска — обычная земля участка, её выравнивают");
    }

    @Test
    void plan_FlatPlot_NothingToDo() {
        PlotLeveling plan = PlotLeveling.plan(ANCHOR, 2, (x, z) -> 64);

        assertEquals(List.of(), plan.cut(), "Ровный участок не срезается");
        assertEquals(List.of(), plan.fill(), "Ровный участок не подсыпается");
    }

    @Test
    void plan_HillInRow_CutToLevelAsOneBox() {
        PlotLeveling plan = PlotLeveling.plan(ANCHOR, 2, (x, z) -> z == 0 && x >= 0 ? 67 : 64);

        assertEquals(
                List.of(new PlotLeveling.Box(0, 64, 0, 2, 66, 0)),
                plan.cut(),
                "Выступ ряда в три блока срезается одним ящиком от уровня ног участка до его верха");
    }

    @Test
    void plan_DeepPit_FilledAtMostTen() {
        PlotLeveling plan = PlotLeveling.plan(ANCHOR, 1, (x, z) -> x == 0 && z == 0 ? 40 : 64);

        assertEquals(
                List.of(new PlotLeveling.Box(0, 54, 0, 0, 63, 0)),
                plan.fill(),
                "Яма глубже десяти подсыпается только на десять блоков (решение 16)");
        assertEquals(10, plan.fillVolume(), "Подсыпка — ровно десять блоков");
    }

    @Test
    void plan_HighCliff_CutAtMostTen() {
        PlotLeveling plan = PlotLeveling.plan(ANCHOR, 1, (x, z) -> x == 1 && z == 1 ? 90 : 64);

        assertEquals(
                List.of(new PlotLeveling.Box(1, 64, 1, 1, 73, 1)),
                plan.cut(),
                "Скала выше десяти срезается только на десять блоков (решение 16)");
    }

    @Test
    void level_MostColumnsEven_MedianIgnoresBuildings() {
        int level = PlotLeveling.level(ANCHOR, 2, (x, z) -> x == 0 ? 70 : 64);

        assertEquals(64, level, "Средняя высота — медиана: постройка на части колонок якорь не сдвигает");
    }
}
