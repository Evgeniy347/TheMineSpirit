package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SettlementStageTests {

    @Test
    void of_NoHouse_FirstStage() {
        assertEquals(1, SettlementStage.of(new BoardLedger()), "До первого дома поселение на первом этапе");
    }

    @Test
    void of_HouseLevelTwoBuilt_SecondStage() {
        BoardLedger ledger = new BoardLedger();
        ledger.note(BoardCase.BUILD_HOUSE, 1);
        ledger.note(BoardCase.BUILD_HOUSE, 2);

        assertEquals(2, SettlementStage.of(ledger), "Этап — старший достроенный уровень дома");
    }

    @Test
    void logCap_ByStage_DoublesFromSixtyFour() {
        assertEquals(64, SettlementStage.logCap(1), "Первый этап — 64 бревна (владелец 23.10)");
        assertEquals(128, SettlementStage.logCap(2), "Каждый следующий этап удваивает предел");
        assertEquals(256, SettlementStage.logCap(3), "Каждый следующий этап удваивает предел");
    }
}
