package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class PlotHeightTests {
    private static final BlockCoord CENTER = new BlockCoord(0, 64, 0);
    private static final int HALF = 5;
    private static final LevelingRules RULES = new LevelingRules(25, 128, 64, 32);
    private static final IntBinaryOperator SLOPE = (x, z) -> x < 0 ? 66 : x == 0 ? 64 : 62;

    @Test
    void choose_HillOnFlat_MedianKeptCutCoversNoFill() {
        PlotHeight height = PlotHeight.choose(
                square(CENTER, HALF), (x, z) -> Math.abs(x) <= 1 && Math.abs(z) <= 1 ? 68 : 64, RULES, 0);
        assertEquals(
                new PlotHeight(64, 36, 0, 25),
                height,
                "Холм 3×3 на 4 блока над ровным: высота — ровное место, срезка 36 без подсыпки; иначе участок "
                        + "срезается ниже нужного и работа растёт");
    }

    @Test
    void choose_PitInFlat_LevelDropsUntilCutFeedsFill() {
        PlotHeight height = PlotHeight.choose(
                square(CENTER, HALF), (x, z) -> Math.abs(x) <= 1 && Math.abs(z) <= 1 ? 60 : 64, RULES, 0);
        assertEquals(
                new PlotHeight(63, 112, 27, 25),
                height,
                "Яма 3×3 на 4 блока: на медиане 64 земли со срезки нет — высота опускается на блок, срезка 112 "
                        + "кормит подсыпку 27 с запасом; иначе дух копает землю за участком (INC-73)");
    }

    @Test
    void choose_MixedSlope_MedianWithoutSurplusRejected() {
        PlotHeight height = PlotHeight.choose(square(CENTER, HALF), SLOPE, RULES, 0);
        assertEquals(
                63,
                height.level(),
                "Склон 66/64/62: на медиане 64 срезка 110 равна подсыпке 110 без запаса — выбирается 63; " + height);
        assertTrue(
                RULES.enough(height.cut(), height.fill()),
                "Выбранная высота даёт срезку ≥ подсыпки × (1 + запас): " + height.describe());
    }

    @Test
    void choose_WaterBelow_LevelStaysAbove() {
        PlotHeight height = PlotHeight.choose(square(CENTER, HALF), (x, z) -> 60, RULES, 67);

        assertEquals(67, height.level(), "Уровень воды — жёсткая нижняя граница: поселение не опускают под воду");
        assertEquals(0, height.cut(), "Над водой рельеф только поднимают, срезки нет");
        assertTrue(height.fill() > 0, "Колонки ниже воды подсыпаются до сухого уровня: " + height.describe());
    }

    @Test
    void describe_Height_LineForLogAndJournal() {
        assertEquals(
                "[plot] высота 63: срезка 176, подсыпка 55, запас 25%",
                PlotHeight.choose(square(CENTER, HALF), SLOPE, RULES, 0).describe(),
                "Строка [plot] в логе и журнале доски называет высоту, срезку, подсыпку и запас — по ней видно, "
                        + "почему выбрана высота");
    }

    private static ColumnMask square(BlockCoord center, int half) {
        return ColumnMask.rect(center.x() - half, center.z() - half, center.x() + half, center.z() + half);
    }
}
