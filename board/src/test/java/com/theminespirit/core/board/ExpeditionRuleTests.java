package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState;
import org.junit.jupiter.api.Test;

class ExpeditionRuleTests {
    @Test
    void ready_MineNotBuilt_NoExpedition() {
        assertFalse(
                ExpeditionRule.ready(GraphState.Built.PLANNED, true),
                "поход копает в шахте: пока она не достроена, он только уводит духов со стройки этапа"
                        + " Иначе тест ready_MineNotBuilt_NoExpedition перестанет ловить эту поломку. При смене ready_MineNotBuilt_NoExpedition проверь это утверждение.");
        assertFalse(
                ExpeditionRule.ready(null, true),
                "шахта неизвестна: поход не начинается"
                        + " Иначе тест ready_MineNotBuilt_NoExpedition перестанет ловить эту поломку. При смене ready_MineNotBuilt_NoExpedition проверь это утверждение.");
    }

    @Test
    void ready_MineBuiltAndOreLacking_Goes() {
        assertTrue(
                ExpeditionRule.ready(GraphState.Built.BUILT, true),
                "шахта готова, руды мало: поход нужен"
                        + " Иначе тест ready_MineBuiltAndOreLacking_Goes перестанет ловить эту поломку. При смене ready_MineBuiltAndOreLacking_Goes проверь это утверждение.");
        assertFalse(
                ExpeditionRule.ready(GraphState.Built.BUILT, false),
                "руды хватает: поход не нужен"
                        + " Иначе тест ready_MineBuiltAndOreLacking_Goes перестанет ловить эту поломку. При смене ready_MineBuiltAndOreLacking_Goes проверь это утверждение.");
    }
}
