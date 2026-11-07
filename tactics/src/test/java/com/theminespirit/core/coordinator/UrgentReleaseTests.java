package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UrgentReleaseTests {
    @Test
    void urgentOrderEnded_WorkingSpirit_HoldsDirectiveInsteadOfReset() {
        assertTrue(
                UrgentRelease.holdInsteadOfReset(true),
                "Снятие срочного приказа у занятого духа удерживает приказ: иначе режим обороны сбрасывается "
                        + "посреди работы и мигает GUARD_AREA ↔ DEFENSIVE (observe24 B8)");
    }

    @Test
    void urgentOrderEnded_IdleSpirit_ReleasesDirective() {
        assertTrue(
                !UrgentRelease.holdInsteadOfReset(false),
                "Свободный дух после снятия срочности приказ отпускает: держать нечего");
    }
}
