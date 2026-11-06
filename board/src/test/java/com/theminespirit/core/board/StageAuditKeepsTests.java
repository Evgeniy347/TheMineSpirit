package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StageAuditKeepsTests {
    @Test
    void keeps_Backoff_DoesNotReopenFinishedBuilding() {
        assertTrue(
                StageAudit.keeps(BoardOutcome.BACKOFF),
                "Ремонт на паузе после провала — не повод снимать отметку готовой постройки: именно BACKOFF "
                        + "переоткрывал first:build_mine через 26 с после DONE");
        assertTrue(StageAudit.keeps(BoardOutcome.ACCEPTED), "Поставленный ремонт закрывает шаг без переоткрытия");
        assertTrue(StageAudit.keeps(BoardOutcome.DUPLICATE), "Уже стоящий ремонт закрывает шаг без переоткрытия");
        assertFalse(
                StageAudit.keeps(BoardOutcome.STAGE_LOCKED),
                "Постройку чужого этапа не чинят: такой исход оставляет шаг на переоткрытие");
    }
}
