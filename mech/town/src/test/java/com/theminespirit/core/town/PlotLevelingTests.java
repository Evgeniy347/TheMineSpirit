package com.theminespirit.core.town;

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
        PlotLeveling plan =
                PlotLeveling.plan(ANCHOR, square(ANCHOR, 2), (x, z) -> x == 1 && z == 1 ? 69 : z == 1 ? 68 : 64);
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
                        + "ящик колонкой на всю высоту рыл траншею, из которой дух не выбирался (сцена 56)"
                        + " При смене cutTopDown_HillWithRuinOnTop_HighestBoxFirst проверь это утверждение.");
    }

    @Test
    void isLevel_StepOfOneBlock_Level() {
        assertTrue(
                PlotLeveling.isLevel(ANCHOR, square(ANCHOR, 11), (x, z) -> x > 0 ? 65 : 64),
                "Ступенька в блок — в допуске: ровный участок не выравнивается по кругу"
                        + " Иначе тест isLevel_StepOfOneBlock_Level перестанет ловить эту поломку. При смене isLevel_StepOfOneBlock_Level проверь это утверждение.");
    }

    @Test
    void isLevel_HumpOfTwoBlocks_NotLevel() {
        assertFalse(
                PlotLeveling.isLevel(ANCHOR, square(ANCHOR, 11), (x, z) -> x == 11 && z == -11 ? 66 : 64),
                "Бугор в два блока у края участка 23×23 — вне допуска: участок без деревьев всё равно выравнивается "
                        + "(INC-50)"
                        + " Иначе тест isLevel_HumpOfTwoBlocks_NotLevel перестанет ловить эту поломку. При смене isLevel_HumpOfTwoBlocks_NotLevel проверь это утверждение.");
    }

    @Test
    void fillBlock_TopLayerOfPlan_AlwaysDirt() {
        assertEquals(
                PlotLeveling.DEFAULT_BLOCK,
                PlotLeveling.fillBlock(63, 64, "cobblestone"),
                "Верхний блок плана выравнивания — всегда земля: булыжник на поверхности участка запрещён"
                        + " Иначе тест fillBlock_TopLayerOfPlan_AlwaysDirt перестанет ловить эту поломку. При смене fillBlock_TopLayerOfPlan_AlwaysDirt проверь это утверждение.");
        assertEquals(
                "cobblestone",
                PlotLeveling.fillBlock(62, 64, "cobblestone"),
                "Под верхним слоем булыжник допустим: подсыпка в глубине может быть из булыжника"
                        + " Иначе тест fillBlock_TopLayerOfPlan_AlwaysDirt перестанет ловить эту поломку. При смене fillBlock_TopLayerOfPlan_AlwaysDirt проверь это утверждение.");
        assertEquals(
                PlotLeveling.DEFAULT_BLOCK,
                PlotLeveling.fillBlock(62, 64, PlotLeveling.DEFAULT_BLOCK),
                "В глубине земля тоже допустима: булыжник — не обязательный материал"
                        + " Иначе тест fillBlock_TopLayerOfPlan_AlwaysDirt перестанет ловить эту поломку. При смене fillBlock_TopLayerOfPlan_AlwaysDirt проверь это утверждение.");
    }

    @Test
    void plan_FlatPlot_NothingToDo() {
        PlotLeveling plan = PlotLeveling.plan(ANCHOR, square(ANCHOR, 2), (x, z) -> 64);

        assertEquals(
                List.of(),
                plan.cut(),
                "Ровный участок не срезается"
                        + " Иначе тест plan_FlatPlot_NothingToDo перестанет ловить эту поломку. При смене plan_FlatPlot_NothingToDo проверь это утверждение.");
        assertEquals(
                List.of(),
                plan.fill(),
                "Ровный участок не подсыпается"
                        + " Иначе тест plan_FlatPlot_NothingToDo перестанет ловить эту поломку. При смене plan_FlatPlot_NothingToDo проверь это утверждение.");
    }

    @Test
    void plan_HillInRow_CutToLevelAsOneBox() {
        PlotLeveling plan = PlotLeveling.plan(ANCHOR, square(ANCHOR, 2), (x, z) -> z == 0 && x >= 0 ? 67 : 64);

        assertEquals(
                List.of(new PlotLeveling.Box(0, 64, 0, 2, 66, 0)),
                plan.cut(),
                "Выступ ряда в три блока срезается одним ящиком от уровня ног участка до его верха"
                        + " Иначе тест plan_HillInRow_CutToLevelAsOneBox перестанет ловить эту поломку. При смене plan_HillInRow_CutToLevelAsOneBox проверь это утверждение.");
    }

    @Test
    void plan_DeepPit_FilledDownToGround() {
        PlotLeveling plan = PlotLeveling.plan(ANCHOR, square(ANCHOR, 1), (x, z) -> x == 0 && z == 0 ? 40 : 64);

        assertEquals(
                List.of(new PlotLeveling.Box(0, 40, 0, 0, 63, 0)),
                plan.fill(),
                "Яма под участком подсыпается до самой земли, а не на десять блоков: иначе дом встанет в воздух "
                        + "над обрывом, а под его полом останется пустота (решение 24.11) — дом не строят в воздухе, "
                        + "низ сначала опирают землёй"
                        + " При смене plan_DeepPit_FilledDownToGround проверь это утверждение.");
        assertEquals(
                24,
                plan.fillVolume(),
                "Подсыпка ямы глубиной 24 — ровно 24 блока: каждый слой от дна до уровня ног учитывается один раз, "
                        + "иначе дно ямы остаётся незакрытым"
                        + " Иначе тест plan_DeepPit_FilledDownToGround перестанет ловить эту поломку. При смене plan_DeepPit_FilledDownToGround проверь это утверждение.");
    }

    @Test
    void plan_HighCliff_CutAtMostTen() {
        PlotLeveling plan = PlotLeveling.plan(ANCHOR, square(ANCHOR, 1), (x, z) -> x == 1 && z == 1 ? 90 : 64);

        assertEquals(
                List.of(new PlotLeveling.Box(1, 64, 1, 1, 73, 1)),
                plan.cut(),
                "Скала выше десяти срезается только на десять блоков (решение 16)"
                        + " Иначе тест plan_HighCliff_CutAtMostTen перестанет ловить эту поломку. При смене plan_HighCliff_CutAtMostTen проверь это утверждение.");
    }

    @Test
    void level_MostColumnsEven_MedianIgnoresBuildings() {
        int level = PlotLeveling.level(ANCHOR, 2, (x, z) -> x == 0 ? 70 : 64);

        assertEquals(
                64,
                level,
                "Средняя высота — медиана: постройка на части колонок якорь не сдвигает"
                        + " Иначе тест level_MostColumnsEven_MedianIgnoresBuildings перестанет ловить эту поломку. При смене level_MostColumnsEven_MedianIgnoresBuildings проверь это утверждение.");
    }

    @Test
    void plan_HillsAndPits_CutNeverBelowLevel() {
        BlockCoord anchor = new BlockCoord(10, 70, -5);
        PlotLeveling leveling = PlotLeveling.plan(anchor, square(anchor, 6), (x, z) -> 70 + ((x * 7 + z * 3) % 9) - 4);

        assertTrue(
                !leveling.cut().isEmpty() && !leveling.fill().isEmpty(),
                "В участке есть и холмы, и ямы"
                        + " Иначе тест plan_HillsAndPits_CutNeverBelowLevel перестанет ловить эту поломку. При смене plan_HillsAndPits_CutNeverBelowLevel проверь это утверждение.");
        assertTrue(
                leveling.cut().stream().allMatch(box -> box.y1() >= anchor.y()),
                "Срезка снимает только клетки на уровне ног и выше: срезка ниже уровня роет яму посреди участка (INC-77), "
                        + "срезка " + leveling.cut()
                        + " Иначе тест plan_HillsAndPits_CutNeverBelowLevel перестанет ловить эту поломку. При смене plan_HillsAndPits_CutNeverBelowLevel проверь это утверждение.");
        assertTrue(
                leveling.fill().stream().allMatch(box -> box.y2() < anchor.y()),
                "Подсыпка — только под уровнем ног: подсыпка " + leveling.fill()
                        + " Иначе тест plan_HillsAndPits_CutNeverBelowLevel перестанет ловить эту поломку. При смене plan_HillsAndPits_CutNeverBelowLevel проверь это утверждение.");
    }

    private static ColumnMask square(BlockCoord center, int half) {
        return ColumnMask.rect(center.x() - half, center.z() - half, center.x() + half, center.z() + half);
    }
}
