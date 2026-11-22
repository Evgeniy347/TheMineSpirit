package com.theminespirit.core.board;

final class SettlementStageTestSupport {
    private SettlementStageTestSupport() {}

    static void noteStorage(BoardLedger ledger) {
        for (String id : new String[] {"storage", "storage_2", "storage_3"}) {
            ledger.notePlan(id, 1);
        }
    }
}
