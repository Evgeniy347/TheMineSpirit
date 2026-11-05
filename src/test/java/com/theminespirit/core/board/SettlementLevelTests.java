package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
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

        assertEquals(71, SettlementLevel.of(plot), "Высота поселения — ровно сохранённая высота участка");
    }

    @Test
    void survey_ChosenLevel_GoesIntoThePlan() {
        ColumnMask area = ColumnMask.rect(-3, -3, 3, 3);

        SettlementPlot plot = SettlementLevel.survey(CENTER, area, (x, z) -> 64, "oak", Integer.MIN_VALUE, RULES);

        assertEquals(64, SettlementLevel.of(plot), "Обмер кладёт в план одну выбранную высоту");
        assertEquals(64, plot.anchor().y(), "Якорь плана — та же высота: второго источника нет");
    }

    @Test
    void survey_WaterRaised_LevelAboveWater() {
        SettlementPlot plot =
                SettlementLevel.survey(CENTER, ColumnMask.rect(-1, -1, 1, 1), (x, z) -> 60, "oak", 70, RULES);

        assertEquals(70, plot.anchor().y(), "Поселение не ставится под воду: нижняя граница — уровень воды");
    }

    @Test
    void of_WithoutPlot_IsAnError() {
        assertThrows(
                NullPointerException.class,
                () -> SettlementLevel.of(null),
                "Без плана высоты нет: молчаливая выдумка снова развела бы постройки по разным y");
    }

    @Test
    void remeasure_OldHeight_RecomputedOnceByTerrain() {
        SettlementPlot stored = new SettlementPlot(new BlockCoord(10, 90, -10), 85, 95, 25, "spruce");

        SettlementPlot moved = SettlementLevel.remeasure(
                stored, ColumnMask.rect(-3, -3, 3, 3), (x, z) -> 64, Integer.MIN_VALUE, RULES);

        assertEquals(64, SettlementLevel.of(moved), "Старая высота 90 пересчитана по рельефу: медиана 64");
        assertEquals(10, moved.anchor().x(), "Миграция сохраняет центр участка: x не переносится");
        assertEquals(-10, moved.anchor().z(), "Миграция сохраняет центр участка: z не переносится");
        assertEquals("spruce", moved.wood(), "Миграция сохраняет выбранную породу построек");
    }

    @Test
    void remeasure_Water_LevelNotBelowWater() {
        SettlementPlot stored = new SettlementPlot(new BlockCoord(0, 90, 0), 85, 95, 25, "oak");

        SettlementPlot moved =
                SettlementLevel.remeasure(stored, ColumnMask.rect(-1, -1, 1, 1), (x, z) -> 60, 70, RULES);

        assertEquals(70, SettlementLevel.of(moved), "Высота после миграции не опускается под воду: 70");
    }

    @Test
    void marked_OnlyOwnRule_IsTrue() {
        assertTrue(SettlementLevel.marked(SettlementLevel.RULE), "Отметка своего правила читается");
        assertFalse(SettlementLevel.marked(""), "Старое сохранение без отметки обязано пересчитаться");
        assertFalse(SettlementLevel.marked("SettlementLevel.v0"), "Чужая версия правила — не своя отметка");
    }

    @Test
    void finalArea_FinalStage_NotFirstStage() {
        TownPlan plan = TownFixtures.flatPlan();
        TownRules rules = TownRules.load();
        int last = StageRules.load().plans().size();
        ColumnMask finalArea = StageSites.levelArea(plan, last, rules)
                .orElseThrow(() -> new AssertionError("План держит места финального этапа"));
        ColumnMask first = StageSites.levelArea(plan, 1, rules).orElseThrow();

        assertNotEquals(first, finalArea, "Финальный этап занимает другую площадь, чем первый: правило различает их");
        assertEquals(
                finalArea,
                SettlementLevel.finalArea(plan).orElseThrow(),
                "Высота считается по площади финального этапа, а не по площадке первого");
    }
}
