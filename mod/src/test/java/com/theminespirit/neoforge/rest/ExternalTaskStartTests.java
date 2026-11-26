package com.theminespirit.neoforge.rest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.engine.RunStatus;
import org.junit.jupiter.api.Test;

class ExternalTaskStartTests {
    @Test
    void refusal_RunningTask_RejectsExternalStart() {
        assertTrue(
                ExternalTaskStart.refusal(RunStatus.RUNNING, false).isPresent(),
                "Запущенная внешняя задача не должна перезаписываться новым REST-запросом: иначе теряется "
                        + "уже выполняемый план. При смене refusal_RunningTask_RejectsExternalStart проверь "
                        + "SpiritTaskGateway.start и startGoal.");
    }

    @Test
    void refusal_BoardAssignedWithoutRunningTask_RejectsExternalStart() {
        assertTrue(
                ExternalTaskStart.refusal(RunStatus.SUCCEEDED, true).isPresent(),
                "Даже между завершением скрипта и закрытием билета внешний старт запрещён: иначе стратег "
                        + "захватывает духа в окне TicketRun и колонка выполнения остаётся пустой. При смене "
                        + "refusal_BoardAssignedWithoutRunningTask_RejectsExternalStart проверь "
                        + "SpiritTaskGateway.boardAssigned и BoardSettlementDesk.");
    }

    @Test
    void refusal_IdleUnassignedSpirit_AllowsExternalStart() {
        assertFalse(
                ExternalTaskStart.refusal(RunStatus.SUCCEEDED, false).isPresent(),
                "Свободный дух без билета должен принимать внешнюю задачу: иначе стратег потеряет единственный "
                        + "канал помощи. При смене refusal_IdleUnassignedSpirit_AllowsExternalStart проверь "
                        + "SpiritTaskGateway.rejectIfBusy и DispatchRestHold.");
    }
}
