package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class PlotHeightTests {
    private static final BlockCoord CENTER = new BlockCoord(0, 64, 0);
    private static final int HALF = 5;
    private static final LevelingRules RULES = new LevelingRules(25, 128, 64);
    private static final IntBinaryOperator SLOPE = (x, z) -> x < 0 ? 66 : x == 0 ? 64 : 62;

    @Test
    void choose_HillOnFlat_MedianKeptCutCoversNoFill() {
        PlotHeight height =
                PlotHeight.choose(CENTER, HALF, (x, z) -> Math.abs(x) <= 1 && Math.abs(z) <= 1 ? 68 : 64, RULES);
        assertEquals(
                new PlotHeight(64, 36, 0, 25),
                height,
                "Холм 3×3 на 4 блока над ровным: высота — ровное место, срезка 36 без подсыпки; иначе участок "
                        + "срезается ниже нужного и работа растёт");
    }

    @Test
    void choose_PitInFlat_LevelDropsUntilCutFeedsFill() {
        PlotHeight height =
                PlotHeight.choose(CENTER, HALF, (x, z) -> Math.abs(x) <= 1 && Math.abs(z) <= 1 ? 60 : 64, RULES);
        assertEquals(
                new PlotHeight(63, 112, 27, 25),
                height,
                "Яма 3×3 на 4 блока: на медиане 64 земли со срезки нет — высота опускается на блок, срезка 112 "
                        + "кормит подсыпку 27 с запасом; иначе дух копает землю за участком (INC-73)");
    }

    @Test
    void choose_MixedSlope_MedianWithoutSurplusRejected() {
        PlotHeight height = PlotHeight.choose(CENTER, HALF, SLOPE, RULES);
        assertEquals(
                63,
                height.level(),
                "Склон 66/64/62: на медиане 64 срезка 110 равна подсыпке 110 без запаса — выбирается 63; " + height);
        assertTrue(
                RULES.enough(height.cut(), height.fill()),
                "Выбранная высота даёт срезку ≥ подсыпки × (1 + запас): " + height.describe());
    }

    @Test
    void describe_Height_LineForLogAndJournal() {
        assertEquals(
                "[plot] высота 63: срезка 176, подсыпка 55, запас 25%",
                PlotHeight.choose(CENTER, HALF, SLOPE, RULES).describe(),
                "Строка [plot] в логе и журнале доски называет высоту, срезку, подсыпку и запас — по ней видно, "
                        + "почему выбрана высота");
    }

    @Test
    void recheck_StoredMedianBreaksRule_PlanRecalculated() {
        SettlementPlot stored = new SettlementPlot(CENTER, 62, 66, 121, "oak");
        Optional<PlotHeight> better = PlotHeight.recheck(stored, HALF, SLOPE, RULES);
        assertEquals(
                Optional.of(63),
                better.map(PlotHeight::level),
                "Сохранённая медиана 64 без запаса земли на неровном участке пересчитывается в 63: живой мир с "
                        + "высотой старой сборки не копает ямы за оградой (INC-73)");
    }

    @Test
    void recheck_LevelPlot_StoredKept() {
        SettlementPlot stored = new SettlementPlot(CENTER, 64, 64, 121, "oak");
        assertEquals(
                Optional.empty(),
                PlotHeight.recheck(stored, HALF, (x, z) -> 64, RULES),
                "Выровненный участок не пересчитывается: высота с постройками на нём не плывёт");
    }
}
