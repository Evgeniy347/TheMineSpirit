package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageSites;
import com.theminespirit.core.town.TownFixtures;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownRules;
import org.junit.jupiter.api.Test;

class SettlementLevelTests {
    private static final BlockCoord CENTER = new BlockCoord(0, 64, 0);
    private static final LevelingRules RULES = new LevelingRules(25, 128, 64, 32);

    @Test
    void of_Plot_IsTheOneHeight() {
        SettlementPlot plot = new SettlementPlot(new BlockCoord(4, 71, -9), 65, 80, 121, "oak");

        assertEquals(
                71,
                SettlementLevel.of(plot),
                "Высота поселения — ровно сохранённая высота участка"
                        + " Иначе тест of_Plot_IsTheOneHeight перестанет ловить эту поломку. При смене of_Plot_IsTheOneHeight проверь это утверждение.");
    }

    @Test
    void survey_ChosenLevel_GoesIntoThePlan() {
        ColumnMask area = ColumnMask.rect(-3, -3, 3, 3);

        SettlementPlot plot = SettlementLevel.survey(CENTER, area, (x, z) -> 64, "oak", Integer.MIN_VALUE, RULES);

        assertEquals(
                64,
                SettlementLevel.of(plot),
                "Обмер кладёт в план одну выбранную высоту"
                        + " Иначе тест survey_ChosenLevel_GoesIntoThePlan перестанет ловить эту поломку. При смене survey_ChosenLevel_GoesIntoThePlan проверь это утверждение.");
        assertEquals(
                64,
                plot.anchor().y(),
                "Якорь плана — та же высота: второго источника нет"
                        + " Иначе тест survey_ChosenLevel_GoesIntoThePlan перестанет ловить эту поломку. При смене survey_ChosenLevel_GoesIntoThePlan проверь это утверждение.");
    }

    @Test
    void survey_WaterRaised_LevelAboveWater() {
        SettlementPlot plot =
                SettlementLevel.survey(CENTER, ColumnMask.rect(-1, -1, 1, 1), (x, z) -> 60, "oak", 70, RULES);

        assertEquals(
                70,
                plot.anchor().y(),
                "Поселение не ставится под воду: нижняя граница — уровень воды"
                        + " Иначе тест survey_WaterRaised_LevelAboveWater перестанет ловить эту поломку. При смене survey_WaterRaised_LevelAboveWater проверь это утверждение.");
    }

    @Test
    void of_WithoutPlot_IsAnError() {
        assertThrows(
                NullPointerException.class,
                () -> SettlementLevel.of(null),
                "Без плана высоты нет: молчаливая выдумка снова развела бы постройки по разным y"
                        + " Иначе тест of_WithoutPlot_IsAnError перестанет ловить эту поломку. При смене of_WithoutPlot_IsAnError проверь это утверждение.");
    }

    @Test
    void remeasure_OldHeight_RecomputedOnceByTerrain() {
        SettlementPlot stored = new SettlementPlot(new BlockCoord(10, 90, -10), 85, 95, 25, "spruce");

        SettlementPlot moved = SettlementLevel.remeasure(
                stored, ColumnMask.rect(-3, -3, 3, 3), (x, z) -> 64, Integer.MIN_VALUE, RULES);

        assertEquals(
                64,
                SettlementLevel.of(moved),
                "Старая высота 90 пересчитана по рельефу: медиана 64"
                        + " Иначе тест remeasure_OldHeight_RecomputedOnceByTerrain перестанет ловить эту поломку. При смене remeasure_OldHeight_RecomputedOnceByTerrain проверь это утверждение.");
        assertEquals(
                10,
                moved.anchor().x(),
                "Миграция сохраняет центр участка: x не переносится"
                        + " Иначе тест remeasure_OldHeight_RecomputedOnceByTerrain перестанет ловить эту поломку. При смене remeasure_OldHeight_RecomputedOnceByTerrain проверь это утверждение.");
        assertEquals(
                -10,
                moved.anchor().z(),
                "Миграция сохраняет центр участка: z не переносится"
                        + " Иначе тест remeasure_OldHeight_RecomputedOnceByTerrain перестанет ловить эту поломку. При смене remeasure_OldHeight_RecomputedOnceByTerrain проверь это утверждение.");
        assertEquals(
                "spruce",
                moved.wood(),
                "Миграция сохраняет выбранную породу построек"
                        + " Иначе тест remeasure_OldHeight_RecomputedOnceByTerrain перестанет ловить эту поломку. При смене remeasure_OldHeight_RecomputedOnceByTerrain проверь это утверждение.");
    }

    @Test
    void remeasure_Water_LevelNotBelowWater() {
        SettlementPlot stored = new SettlementPlot(new BlockCoord(0, 90, 0), 85, 95, 25, "oak");

        SettlementPlot moved =
                SettlementLevel.remeasure(stored, ColumnMask.rect(-1, -1, 1, 1), (x, z) -> 60, 70, RULES);

        assertEquals(
                70,
                SettlementLevel.of(moved),
                "Высота после миграции не опускается под воду: 70"
                        + " Иначе тест remeasure_Water_LevelNotBelowWater перестанет ловить эту поломку. При смене remeasure_Water_LevelNotBelowWater проверь это утверждение.");
    }

    @Test
    void site_PlotWood_GoesIntoStageSaplings() {
        TownPlan plan = TownFixtures.flatPlan();
        SettlementPlot plot = new SettlementPlot(new BlockCoord(200, 70, -300), 65, 80, 100, "birch");

        StageSite site = SettlementLevel.site(plan, 1, plot)
                .orElseThrow(() -> new AssertionError("План этапа 1 собирается по размеченному участку"));

        assertEquals(
                "birch",
                site.wood(),
                "Порода главного сада берётся из участка поселения: иначе план сажает oak при выбранной birch "
                        + "и посадка расходится с проверкой плана"
                        + " При смене site_PlotWood_GoesIntoStageSaplings проверь это утверждение.");
    }

    @Test
    void marked_OnlyOwnRule_IsTrue() {
        assertTrue(
                SettlementLevel.marked(SettlementLevel.RULE),
                "Отметка своего правила читается"
                        + " Иначе тест marked_OnlyOwnRule_IsTrue перестанет ловить эту поломку. При смене marked_OnlyOwnRule_IsTrue проверь это утверждение.");
        assertFalse(
                SettlementLevel.marked(""),
                "Старое сохранение без отметки обязано пересчитаться"
                        + " Иначе тест marked_OnlyOwnRule_IsTrue перестанет ловить эту поломку. При смене marked_OnlyOwnRule_IsTrue проверь это утверждение.");
        assertFalse(
                SettlementLevel.marked("SettlementLevel.v0"),
                "Чужая версия правила — не своя отметка"
                        + " Иначе тест marked_OnlyOwnRule_IsTrue перестанет ловить эту поломку. При смене marked_OnlyOwnRule_IsTrue проверь это утверждение.");
    }

    @Test
    void finalArea_FinalStage_NotFirstStage() {
        TownPlan plan = TownFixtures.flatPlan();
        TownRules rules = TownRules.load();
        int last = StageRules.load().plans().size();
        ColumnMask finalArea = StageSites.levelArea(plan, last, rules)
                .orElseThrow(() -> new AssertionError("План держит места финального этапа"));
        ColumnMask first = StageSites.levelArea(plan, 1, rules).orElseThrow();

        assertNotEquals(
                first,
                finalArea,
                "Финальный этап занимает другую площадь, чем первый: правило различает их"
                        + " Иначе тест finalArea_FinalStage_NotFirstStage перестанет ловить эту поломку. При смене finalArea_FinalStage_NotFirstStage проверь это утверждение.");
        assertEquals(
                finalArea,
                SettlementLevel.finalArea(plan).orElseThrow(),
                "Высота считается по площади финального этапа, а не по площадке первого"
                        + " Иначе тест finalArea_FinalStage_NotFirstStage перестанет ловить эту поломку. При смене finalArea_FinalStage_NotFirstStage проверь это утверждение.");
    }
}
