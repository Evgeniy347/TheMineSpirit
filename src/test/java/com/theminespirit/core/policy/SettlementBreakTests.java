package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SettlementBreakTests {
    @Test
    void of_OwnFenceOfOldLayoutOnPlot_Settlement() {
        assertEquals(
                SettlementBreak.Check.SETTLEMENT,
                SettlementBreak.of(SettlementBreak.Origin.SPIRIT, SettlementBreak.Place.PLOT),
                "Свой забор прежней раскладки на участке вне клеток плана — остаток: его убирают, а не держат чёрным "
                        + "списком (INC-72: jungle_fence у (-160,71,-497))");
    }

    @Test
    void of_OwnBlockInPlanCell_Own() {
        assertEquals(
                SettlementBreak.Check.OWN,
                SettlementBreak.of(SettlementBreak.Origin.SPIRIT, SettlementBreak.Place.PLAN),
                "Своё в клетке постройки плана — как своё: его держит запрет по плану");
    }

    @Test
    void of_PlayerPlacedOnPlot_Default() {
        assertEquals(
                SettlementBreak.Check.DEFAULT,
                SettlementBreak.of(SettlementBreak.Origin.PLAYER, SettlementBreak.Place.PLOT),
                "Поставленное игроком защищено и на участке поселения");
    }
}
