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
    void cutTopDown_HillWithRuinOnTop_HighestBoxFirst() {
        PlotLeveling plan = PlotLeveling.plan(ANCHOR, 2, (x, z) -> x == 1 && z == 1 ? 69 : z == 1 ? 68 : 64);
        List<PlotLeveling.Box> layers = plan.cutTopDown();
        assertEquals(
                List.of(
                        new PlotLeveling.Box(1, 68, 1, 1, 68, 1),
                        new PlotLeveling.Box(-2, 67, 1, 2, 67, 1),
                        new PlotLeveling.Box(-2, 66, 1, 2, 66, 1),
                        new PlotLeveling.Box(-2, 65, 1, 2, 65, 1),
                        new PlotLeveling.Box(-2, 64, 1, 2, 64, 1)),
                layers,
                "Срезка с вершин слоями в блок: стенка на холме (ноги 69) — раньше холма, иначе висит без стоянки; "
                        + "ящик колонкой на всю высоту рыл траншею, из которой дух не выбирался (сцена 56)");
    }

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
        assertTrue(
                SettlementLayout.mineColumn(ANCHOR, entrance.x() + 2, entrance.z()),
                "Стенка спуска (x+2 от угла входа) — часть спуска: её срезка выравниванием шла по кругу (сцена 56)");
        assertFalse(
                SettlementLayout.mineColumn(ANCHOR, entrance.x() + 3, entrance.z()),
                "Колонка за стенкой спуска — обычная земля участка, её выравнивают");
        assertFalse(
                SettlementLayout.mineColumn(ANCHOR, entrance.x() - 1, entrance.z()),
                "Колонка перед стенкой спуска — обычная земля участка, её выравнивают");
    }

    @Test
    void voids_CaveUnderPlot_OnlyWithinDepth() {
        // Колонка (0,0): поверхность 64, пещера 58..62 под ней — засыпается только 60..62 (4 под уровнем), глубже нет.
        List<PlotLeveling.Box> voids = PlotLeveling.voids(ANCHOR, 0, (x, z) -> 64, (x, y, z) -> y >= 58 && y <= 62);

        assertEquals(
                List.of(new PlotLeveling.Box(0, 60, 0, 0, 62, 0)),
                voids,
                "Пустота под участком на глубину до 4 засыпается, глубже — не цель (INC-67): дух 10 минут бродил по "
                        + "старой пещере на 7 ниже участка");
    }

    @Test
    void voids_MineColumn_Skipped() {
        assertEquals(
                List.of(),
                PlotLeveling.voids(ANCHOR, 0, (x, z) -> Integer.MIN_VALUE, (x, y, z) -> true),
                "Спуск в шахту — не пустота: его колонку выравнивание не засыпает");
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
