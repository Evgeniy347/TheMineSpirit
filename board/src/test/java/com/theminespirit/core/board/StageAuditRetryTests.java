package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StageAuditRetryTests {
    @Test
    void repairRetry_FailedPastBackoff_RetriesButNotDuringBackoff() {
        assertTrue(
                StageAudit.repairRetry(BoardStatus.FAILED, false),
                "Проваленный ремонт шахты повторяется после паузы: иначе он висит FAILED/BLOCKED навсегда, "
                        + "даже когда кирка и булыжник появились"
                        + " При смене repairRetry_FailedPastBackoff_RetriesButNotDuringBackoff проверь это утверждение.");
        assertFalse(
                StageAudit.repairRetry(BoardStatus.FAILED, true),
                "Во время паузы после провала ремонт не перевыдаётся каждую пересборку"
                        + " Иначе тест repairRetry_FailedPastBackoff_RetriesButNotDuringBackoff перестанет ловить эту поломку. При смене repairRetry_FailedPastBackoff_RetriesButNotDuringBackoff проверь это утверждение.");
        assertTrue(
                StageAudit.repairRetry(BoardStatus.BLOCKED, false),
                "Снятая блокировка возвращает ремонт"
                        + " Иначе тест repairRetry_FailedPastBackoff_RetriesButNotDuringBackoff перестанет ловить эту поломку. При смене repairRetry_FailedPastBackoff_RetriesButNotDuringBackoff проверь это утверждение.");
        assertFalse(
                StageAudit.repairRetry(BoardStatus.PENDING, false),
                "Обычное ожидание не трогаем"
                        + " Иначе тест repairRetry_FailedPastBackoff_RetriesButNotDuringBackoff перестанет ловить эту поломку. При смене repairRetry_FailedPastBackoff_RetriesButNotDuringBackoff проверь это утверждение.");
        assertFalse(
                StageAudit.repairRetry(null, false),
                "Нового ремонта ещё нет на доске — повтор не падает и не перевыдаёт"
                        + " Иначе тест repairRetry_FailedPastBackoff_RetriesButNotDuringBackoff перестанет ловить эту поломку. При смене repairRetry_FailedPastBackoff_RetriesButNotDuringBackoff проверь это утверждение.");
    }
}
