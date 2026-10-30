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

    @Test
    void place_PlanCellOffPlot_IsPlan() {
        assertEquals(
                SettlementBreak.Place.PLAN,
                SettlementBreak.place(false, true),
                "Клетка плана за оградой участка — всё равно план: своя стенка или факел спуска шахты уходили "
                        + "в чёрный список защиты, если участок кончался раньше шахты (observe8: wall_torch держал "
                        + "шахту на 75%)");
        assertEquals(
                SettlementBreak.Place.OFF_PLOT,
                SettlementBreak.place(false, false),
                "Вне участка и вне плана — по-прежнему чужая земля: послабление только для клеток плана");
    }
}
