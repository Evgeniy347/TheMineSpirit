package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import org.junit.jupiter.api.Test;

class BoardLedgerOwnerTests {
    private static final BoardCase CASE = BoardCase.CRAFTING_STATION;

    @Test
    void reopen_GraphStationAfterLegacyDone_KeepsSharedMark() {
        BoardLedger ledger = new BoardLedger();
        ledger.complete(CASE, 1, "first:crafting_station");
        ledger.complete(CASE, 1, "graph:station:crafting_table");

        ledger.reopen(CASE, 1, "graph:station:crafting_table");

        assertTrue(
                ledger.has(CASE, 1),
                "Переоткрытие graph-станции не стирает DONE legacy-билета: иначе верстак снова становится "
                        + "PLANNED и цепочка инструментов встаёт. При смене "
                        + "reopen_GraphStationAfterLegacyDone_KeepsSharedMark проверь BoardLedger.owners.");
        ledger.reopen(CASE, 1, "first:crafting_station");
        assertFalse(
                ledger.has(CASE, 1),
                "После снятия обоих владельцев отметка исчезает: иначе устаревший DONE останется навсегда. "
                        + "При смене reopen_GraphStationAfterLegacyDone_KeepsSharedMark проверь очистку owners.");
    }

    @Test
    void reopen_ForeignTicket_DoesNotEraseBuildingMark() {
        BoardLedger ledger = new BoardLedger();
        ledger.complete(CASE, 1, "first:crafting_station");

        ledger.reopen(CASE, 1, "repair:some_other_station");

        assertTrue(
                ledger.has(CASE, 1),
                "Чужой билет того же кейса не снимает отметку: иначе любой ремонт обнуляет готовность "
                        + "постройки. При смене reopen_ForeignTicket_DoesNotEraseBuildingMark проверь проверку владельца.");
    }

    @Test
    void reopen_GenericMarkWithoutOwner_StillForgetsIt() {
        BoardLedger ledger = new BoardLedger();
        ledger.complete(CASE, 1);

        ledger.reopen(CASE, 1);

        assertFalse(
                ledger.has(CASE, 1),
                "Старое общее переоткрытие продолжает работать: иначе откат этапа не снимет старую отметку. "
                        + "При смене reopen_GenericMarkWithoutOwner_StillForgetsIt проверь перегрузку методов.");
    }
}
