package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.town.ColumnMask;
import org.junit.jupiter.api.Test;

class SettlementReachTests {
    private static final ColumnMask TOWN = ColumnMask.rect(-10, -10, 10, 10);

    @Test
    void near_PointInsideSettlement_True() {
        assertTrue(
                SettlementReach.near(TOWN, 0, 0),
                "Точка внутри поселения входит в запретную зону моста: иначе дух снова строит мост по воздуху "
                        + "прямо над поселением. При смене near_PointInsideSettlement_True проверь границы региона.");
    }

    @Test
    void near_ExactlyMarginAway_True() {
        assertTrue(
                SettlementReach.near(TOWN, 110, 0),
                "Ровно 100 блоков от края поселения — ещё запретная зона: иначе мост «в воздухе» появляется "
                        + "у самой ограды, когда дух чуть отошёл от края. При смене near_ExactlyMarginAway_True "
                        + "проверь границу MARGIN.");
    }

    @Test
    void near_BeyondMargin_False() {
        assertFalse(
                SettlementReach.near(TOWN, 111, 0),
                "За сотней блоков от поселения мост разрешён: иначе пострадает спасение в дикой местности, "
                        + "где без моста дух не перейдёт воду. При смене near_BeyondMargin_False проверь MARGIN.");
    }

    @Test
    void near_DiagonalCorner_MeasuresStraightLine() {
        assertTrue(
                SettlementReach.near(TOWN, 80, 80),
                "Диагональ 70 на 70 блоков от угла — внутри круга сотни: иначе угол запретной зоны окажется "
                        + "уже круга и мост появится у ограды наискосок. При смене near_DiagonalCorner_MeasuresStraightLine "
                        + "проверь квадрат расстояния.");
        assertFalse(
                SettlementReach.near(TOWN, 81, 81),
                "Диагональ 71 на 71 блоков — уже за кругом сотни: иначе запрет растянется дальше согласованного "
                        + "радиуса и заденет дальние работы. При смене near_DiagonalCorner_MeasuresStraightLine "
                        + "проверь квадрат расстояния.");
    }

    @Test
    void near_NoSettlement_False() {
        assertFalse(
                SettlementReach.near(null, 0, 0),
                "Без плана поселения запрет не действует: иначе до разметки участка дух остаётся без спасения "
                        + "мостом в дикой местности. При смене near_NoSettlement_False проверь пустой регион.");
    }
}
