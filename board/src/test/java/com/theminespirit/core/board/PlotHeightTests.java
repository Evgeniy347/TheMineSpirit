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
    private static final LevelingRules RULES = new LevelingRules(25, 128, 64, 32, 128, 10);
    private static final IntBinaryOperator SLOPE = (x, z) -> x < 0 ? 66 : x == 0 ? 64 : 62;

    @Test
    void choose_HillOnFlat_MedianKeptCutCoversNoFill() {
        PlotHeight height = PlotHeight.choose(
                square(CENTER, HALF), (x, z) -> Math.abs(x) <= 1 && Math.abs(z) <= 1 ? 68 : 64, RULES, 0);
        assertEquals(
                new PlotHeight(64, 36, 0, 25),
                height,
                "Холм 3×3 на 4 блока над ровным: высота — ровное место, срезка 36 без подсыпки; иначе участок "
                        + "срезается ниже нужного и работа растёт"
                        + " При смене choose_HillOnFlat_MedianKeptCutCoversNoFill проверь это утверждение.");
    }

    @Test
    void choose_PitInFlat_LevelDropsUntilCutFeedsFill() {
        PlotHeight height = PlotHeight.choose(
                square(CENTER, HALF), (x, z) -> Math.abs(x) <= 1 && Math.abs(z) <= 1 ? 60 : 64, RULES, 0);
        assertEquals(
                new PlotHeight(63, 112, 27, 25),
                height,
                "Яма 3×3 на 4 блока: на медиане 64 земли со срезки нет — высота опускается на блок, срезка 112 "
                        + "кормит подсыпку 27 с запасом; иначе дух копает землю за участком (INC-73)"
                        + " При смене choose_PitInFlat_LevelDropsUntilCutFeedsFill проверь это утверждение.");
    }

    @Test
    void choose_MixedSlope_MedianWithoutSurplusRejected() {
        PlotHeight height = PlotHeight.choose(square(CENTER, HALF), SLOPE, RULES, 0);
        assertEquals(
                63,
                height.level(),
                "Склон 66/64/62: на медиане 64 срезка 110 равна подсыпке 110 без запаса — выбирается 63; " + height
                        + " Иначе тест choose_MixedSlope_MedianWithoutSurplusRejected перестанет ловить эту поломку. При смене choose_MixedSlope_MedianWithoutSurplusRejected проверь это утверждение.");
        assertTrue(
                RULES.enough(height.cut(), height.fill(), 0),
                "Выбранная высота даёт срезку ≥ подсыпки × (1 + запас): " + height.describe()
                        + " Иначе тест choose_MixedSlope_MedianWithoutSurplusRejected перестанет ловить эту поломку. При смене choose_MixedSlope_MedianWithoutSurplusRejected проверь это утверждение.");
    }

    @Test
    void choose_WaterBelow_LevelStaysAbove() {
        PlotHeight height = PlotHeight.choose(square(CENTER, HALF), (x, z) -> 60, RULES, 67);

        assertEquals(
                67,
                height.level(),
                "Уровень воды — жёсткая нижняя граница: поселение не опускают под воду"
                        + " Иначе тест choose_WaterBelow_LevelStaysAbove перестанет ловить эту поломку. При смене choose_WaterBelow_LevelStaysAbove проверь это утверждение.");
        assertEquals(
                0,
                height.cut(),
                "Над водой рельеф только поднимают, срезки нет"
                        + " Иначе тест choose_WaterBelow_LevelStaysAbove перестанет ловить эту поломку. При смене choose_WaterBelow_LevelStaysAbove проверь это утверждение.");
        assertTrue(
                height.fill() > 0,
                "Колонки ниже воды подсыпаются до сухого уровня: " + height.describe()
                        + " Иначе тест choose_WaterBelow_LevelStaysAbove перестанет ловить эту поломку. При смене choose_WaterBelow_LevelStaysAbove проверь это утверждение.");
    }

    @Test
    void choose_PitWithDirtStock_KeepsMedian() {
        PlotHeight height = PlotHeight.choose(
                square(CENTER, HALF), (x, z) -> Math.abs(x) <= 1 && Math.abs(z) <= 1 ? 60 : 64, RULES, 0, 45);

        assertEquals(
                64,
                height.level(),
                "Земля со склада оплачивает подсыпку: яма глубиной 4 при 45 блоках земли оставляет высоту 64, "
                        + "а не опускает поселение на 63; иначе тысячи блоков в сундуках не участвуют в выборе "
                        + "и поселение уходит вниз. При смене бюджета подсыпки проверь choose_PitInFlat_LevelDropsUntilCutFeedsFill.");
    }

    @Test
    void waterLevel_FewerThanTenColumns_IsIgnored() {
        int[] few = {62, 62, 63, 61, 62, 62, 63, 61, 62};
        assertTrue(
                WaterLevel.of(few, 10).isEmpty(),
                "Меньше 10 колонок воды — не ограничение: случайная лужа не должна поднимать поселение и "
                        + "ломать выравнивание; иначе одна яма с водой задаёт высоту всему поселению. "
                        + "При смене порога воды проверь waterMinBlocks в leveling.json.");
    }

    @Test
    void waterLevel_TenColumns_MedianIsFloorAboveWater() {
        int[] levels = {60, 61, 62, 63, 64, 62, 62, 61, 63, 64};
        WaterLevel water = WaterLevel.of(levels, 10).orElseThrow();

        assertEquals(
                62,
                water.median(),
                "Медиана воды из 10 колонок — 62: ограничение берётся по середине, а не по максимуму или минимуму, "
                        + "иначе одно океанское пятно уводит поселение вверх. При смене waterRadius проверь окно "
                        + "поиска воды вокруг базовой точки.");
        assertEquals(
                63,
                water.minSettlementLevel(),
                "Уровень поселения (ноги) не ниже воды: медиана 62 даёт минимальные ноги 63, верх грунта 62; "
                        + "иначе вода заливает участок. При смене семантики anchor.y проверь "
                        + "SettlementGround.waterScan.");
    }

    @Test
    void describe_Height_LineForLogAndJournal() {
        assertEquals(
                "[plot] высота 63: срезка 176, подсыпка 55, запас 25%",
                PlotHeight.choose(square(CENTER, HALF), SLOPE, RULES, 0).describe(),
                "Строка [plot] в логе и журнале доски называет высоту, срезку, подсыпку и запас — по ней видно, "
                        + "почему выбрана высота"
                        + " Иначе тест describe_Height_LineForLogAndJournal перестанет ловить эту поломку. При смене describe_Height_LineForLogAndJournal проверь это утверждение.");
    }

    private static ColumnMask square(BlockCoord center, int half) {
        return ColumnMask.rect(center.x() - half, center.z() - half, center.x() + half, center.z() + half);
    }
}
