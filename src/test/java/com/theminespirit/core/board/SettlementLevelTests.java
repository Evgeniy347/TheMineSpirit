package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
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
}
