package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnchorReturnTests {
    @Test
    void allowed_RunningTask_NoReturnToAnchor() {
        assertFalse(
                AnchorReturn.allowed(true),
                "Дух с исполняемым заданием не идёт к якорю: возврат спорит с задачей, дух дёргается между "
                        + "целью задания и домом и не доходит никуда (аномалия 7)"
                        + " Иначе тест allowed_RunningTask_NoReturnToAnchor перестанет ловить эту поломку. При смене allowed_RunningTask_NoReturnToAnchor проверь это утверждение.");
    }

    @Test
    void allowed_IdleSpirit_ReturnsToAnchor() {
        assertTrue(
                AnchorReturn.allowed(false),
                "Свободный дух возвращается к якорю: поводок и охрана зоны остаются рабочими"
                        + " Иначе тест allowed_IdleSpirit_ReturnsToAnchor перестанет ловить эту поломку. При смене allowed_IdleSpirit_ReturnsToAnchor проверь это утверждение.");
    }
}
