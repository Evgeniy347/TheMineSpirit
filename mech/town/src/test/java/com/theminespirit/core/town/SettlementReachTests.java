package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettlementReachTests {
    private static final SettlementReach REACH = new SettlementReach(0, 0, 159, 159);

    @Test
    void access_Inside_Allowed() {
        SettlementReach.Access access = SettlementReach.access(Optional.of(REACH), true, 80.5, 80.5);

        assertTrue(access.allowed(), "Внутри участка вкладка крафта работает");
        assertEquals(0, access.distance(), "Внутри участка расстояние до границы — 0");
    }

    @Test
    void access_ExactlyMarginAway_Allowed() {
        SettlementReach.Access access =
                SettlementReach.access(Optional.of(REACH), true, 160 + SettlementReach.MARGIN, 10);

        assertTrue(access.allowed(), "Ровно 64 блока от границы — ещё в зоне (решение владельца 27.10)");
    }

    @Test
    void access_BeyondMargin_TooFar() {
        SettlementReach.Access access = SettlementReach.access(
                Optional.of(REACH), true, -SettlementReach.MARGIN - 1.0, -SettlementReach.MARGIN - 1.0);

        assertEquals(SettlementReach.Verdict.TOO_FAR, access.verdict(), "По диагонали за 64 блока — отказ");
        assertEquals(92, access.distance(), "Расстояние — евклидово до угла участка: √(65²+65²) ≈ 92");
    }

    @Test
    void access_NoSettlement_Refused() {
        assertEquals(
                SettlementReach.Verdict.NO_SETTLEMENT,
                SettlementReach.access(Optional.empty(), true, 0, 0).verdict(),
                "Без плана поселения брать и крафтить нечего: вкладка показывает причину");
    }

    @Test
    void access_OtherDimension_Refused() {
        assertEquals(
                SettlementReach.Verdict.OTHER_DIMENSION,
                SettlementReach.access(Optional.of(REACH), false, 10, 10).verdict(),
                "Из Нижнего мира координаты не сравниваются с участком в верхнем мире");
    }

    @Test
    void of_FencePostOutsideRegion_WidensBorder() {
        ColumnMask empty = new ColumnMask(0, 0, 41, 41, new BitSet());
        TownPlan plan = new TownPlan(
                new BlockCoord(20, 70, 20),
                ColumnMask.rect(0, 0, 40, 40),
                new TownStreets(ColumnMask.rect(0, 0, 40, 0), empty, empty),
                List.of(),
                new FenceLine(List.of(new ColumnMask.Cell(-3, 45)), List.of()),
                List.of());

        SettlementReach reach = SettlementReach.of(plan, List.of(new ColumnMask.Cell(50, 2)));

        assertEquals(
                new SettlementReach(-3, 0, 50, 45),
                reach,
                "Граница — участок плана плюс столбы ограды и кольца стройки: сундук у ограды тоже в поселении");
    }
}
