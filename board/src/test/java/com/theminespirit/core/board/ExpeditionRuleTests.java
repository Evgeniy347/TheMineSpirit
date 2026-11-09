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
                "поход копает в шахте: пока она не достроена, он только уводит духов со стройки этапа");
        assertFalse(ExpeditionRule.ready(null, true), "шахта неизвестна: поход не начинается");
    }

    @Test
    void ready_MineBuiltAndOreLacking_Goes() {
        assertTrue(ExpeditionRule.ready(GraphState.Built.BUILT, true), "шахта готова, руды мало: поход нужен");
        assertFalse(ExpeditionRule.ready(GraphState.Built.BUILT, false), "руды хватает: поход не нужен");
    }
}
