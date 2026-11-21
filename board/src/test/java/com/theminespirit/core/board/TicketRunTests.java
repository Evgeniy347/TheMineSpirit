package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.task.SpiritReading;
import org.junit.jupiter.api.Test;

class TicketRunTests {

    @Test
    void decide_OwnTaskSucceeded_Succeeded() {
        assertEquals(
                TicketRun.SUCCEEDED,
                TicketRun.decide(5, 5, RunStatus.SUCCEEDED, false),
                "Своя задача закрыла"
                        + " Иначе тест decide_OwnTaskSucceeded_Succeeded перестанет ловить эту поломку. При смене decide_OwnTaskSucceeded_Succeeded проверь это утверждение.");
    }

    @Test
    void decide_ForeignTaskFailed_ResumeNotFailed() {
        assertEquals(
                TicketRun.RESUME,
                TicketRun.decide(5, 6, RunStatus.FAILED, false),
                "Провал чужой задачи (фоновой еды, рефлекса) заданию доски не приписывается (INC-31/24)"
                        + " Иначе тест decide_ForeignTaskFailed_ResumeNotFailed перестанет ловить эту поломку. При смене decide_ForeignTaskFailed_ResumeNotFailed проверь это утверждение.");
    }

    @Test
    void decide_ForeignTaskSucceeded_ResumeNotDone() {
        assertEquals(
                TicketRun.RESUME,
                TicketRun.decide(5, 6, RunStatus.SUCCEEDED, false),
                "Успех чужой задачи не закрывает задание: дух продолжает своё"
                        + " Иначе тест decide_ForeignTaskSucceeded_ResumeNotDone перестанет ловить эту поломку. При смене decide_ForeignTaskSucceeded_ResumeNotDone проверь это утверждение.");
    }

    @Test
    void decide_ForeignTaskRunning_Displaced() {
        assertEquals(
                TicketRun.DISPLACED,
                TicketRun.decide(5, 6, RunStatus.RUNNING, false),
                "Чужая задача идёт"
                        + " Иначе тест decide_ForeignTaskRunning_Displaced перестанет ловить эту поломку. При смене decide_ForeignTaskRunning_Displaced проверь это утверждение.");
    }

    @Test
    void decide_CombatAfterForeignTask_Displaced() {
        assertEquals(
                TicketRun.DISPLACED,
                TicketRun.decide(5, 6, RunStatus.SUCCEEDED, true),
                "Пока бой держит духа, задание ждёт его, а не перезапускается"
                        + " Иначе тест decide_CombatAfterForeignTask_Displaced перестанет ловить эту поломку. При смене decide_CombatAfterForeignTask_Displaced проверь это утверждение.");
    }

    @Test
    void decide_OwnTaskCancelledAfterCombat_Resume() {
        assertEquals(
                TicketRun.RESUME,
                TicketRun.decide(5, 5, RunStatus.CANCELLED, false),
                "Бой снял — продолжить"
                        + " Иначе тест decide_OwnTaskCancelledAfterCombat_Resume перестанет ловить эту поломку. При смене decide_OwnTaskCancelledAfterCombat_Resume проверь это утверждение.");
    }

    @Test
    void decide_UnknownGeneration_SettlesByStatus() {
        long unknown = SpiritReading.UNKNOWN_GENERATION;

        assertEquals(
                TicketRun.RUNNING,
                TicketRun.decide(unknown, 6, RunStatus.CANCELLED, false),
                "Без поколения"
                        + " Иначе тест decide_UnknownGeneration_SettlesByStatus перестанет ловить эту поломку. При смене decide_UnknownGeneration_SettlesByStatus проверь это утверждение.");
    }

    @Test
    void decide_UnknownGenerationForeignFinish_ResumeOwnPlan() {
        long unknown = SpiritReading.UNKNOWN_GENERATION;

        assertEquals(
                TicketRun.RESUME,
                TicketRun.decide(unknown, 6, RunStatus.SUCCEEDED, false),
                "После рестарта поколение задания неизвестно: успех чужого скрипта не закрывает задание без "
                        + "факта мира, дух заново стартует план этого задания (живой разбор блоков закрывался "
                        + "успехом, когда дух рубил деревья в стороне)"
                        + " Иначе тест decide_UnknownGenerationForeignFinish_ResumeOwnPlan перестанет ловить эту поломку. При смене decide_UnknownGenerationForeignFinish_ResumeOwnPlan проверь это утверждение.");
        assertEquals(
                TicketRun.RESUME,
                TicketRun.decide(unknown, 6, RunStatus.FAILED, false),
                "Провал чужого скрипта после рестарта тоже не записывается в задание: сначала свой план"
                        + " Иначе тест decide_UnknownGenerationForeignFinish_ResumeOwnPlan перестанет ловить эту поломку. При смене decide_UnknownGenerationForeignFinish_ResumeOwnPlan проверь это утверждение.");
    }

    @Test
    void decide_UnknownGenerationRunning_ResumeOwnPlan() {
        long unknown = SpiritReading.UNKNOWN_GENERATION;

        assertEquals(
                TicketRun.RESUME,
                TicketRun.decide(unknown, 6, RunStatus.RUNNING, false),
                "После рестарта поколение задания неизвестно, а дух может выполнять чужой фоновый скрипт: "
                        + "прогресс такого скрипта заданию не принадлежит, поэтому задание заново стартует свой план "
                        + "(observe15 B2), иначе сторож прогресса берёт чужой RunProgress"
                        + " При смене decide_UnknownGenerationRunning_ResumeOwnPlan проверь это утверждение.");
    }

    @Test
    void restoredTask_UnknownGeneration_OwnedBySnapshot() {
        assertTrue(
                TicketRun.restoredTask(SpiritReading.UNKNOWN_GENERATION),
                "У задачи из снимка нет поколения: сброс сборки вправе снять её как задание доски"
                        + " Иначе тест restoredTask_UnknownGeneration_OwnedBySnapshot перестанет ловить эту поломку. При смене restoredTask_UnknownGeneration_OwnedBySnapshot проверь это утверждение.");
    }

    @Test
    void restoredTask_KnownGeneration_NotOwnedBySnapshot() {
        assertFalse(
                TicketRun.restoredTask(7),
                "Дух с новым поколением уже получил другую задачу (REST, команда, граф): сброс сборки не вправе "
                        + "отменять её вместо старого задания доски"
                        + " Иначе тест restoredTask_KnownGeneration_NotOwnedBySnapshot перестанет ловить эту поломку. При смене restoredTask_KnownGeneration_NotOwnedBySnapshot проверь это утверждение.");
    }

    @Test
    void ownsTask_SameGeneration_IsCancellable() {
        assertTrue(
                TicketRun.ownsTask(5, 5),
                "Дух выполняет задачу этого билета: явная отмена снимает её"
                        + " Иначе тест ownsTask_SameGeneration_IsCancellable перестанет ловить эту поломку. При смене ownsTask_SameGeneration_IsCancellable проверь это утверждение.");
    }

    @Test
    void ownsTask_ForeignGeneration_IsNotCancellable() {
        assertFalse(
                TicketRun.ownsTask(5, 6),
                "Дух уже получил другую задачу (REST, команда, граф): отмена задания доски не вправе снимать её "
                        + "вместо своего билета (debtmisc3)"
                        + " Иначе тест ownsTask_ForeignGeneration_IsNotCancellable перестанет ловить эту поломку. При смене ownsTask_ForeignGeneration_IsNotCancellable проверь это утверждение.");
    }

    @Test
    void ownsTask_UnknownIssuedGeneration_IsCancellable() {
        long unknown = SpiritReading.UNKNOWN_GENERATION;

        assertTrue(
                TicketRun.ownsTask(unknown, 6),
                "Поколение выдачи неизвестно (снимок/старый билет): сверять нечем, поэтому задача считается своей"
                        + " Иначе тест ownsTask_UnknownIssuedGeneration_IsCancellable перестанет ловить эту поломку. При смене ownsTask_UnknownIssuedGeneration_IsCancellable проверь это утверждение.");
    }

    @Test
    void ownsTask_UnknownCurrentGeneration_IsCancellable() {
        long unknown = SpiritReading.UNKNOWN_GENERATION;

        assertTrue(
                TicketRun.ownsTask(5, unknown),
                "У восстановленной задачи нет поколения: она принадлежит снимку, и отмена билета вправе её снять"
                        + " Иначе тест ownsTask_UnknownCurrentGeneration_IsCancellable перестанет ловить эту поломку. При смене ownsTask_UnknownCurrentGeneration_IsCancellable проверь это утверждение.");
    }

    @Test
    void decide_UnknownGenerationRunningWithoutTarget_StaysRunning() {
        long unknown = SpiritReading.UNKNOWN_GENERATION;

        assertEquals(
                TicketRun.RUNNING,
                TicketRun.decide(unknown, 6, RunStatus.RUNNING, false, false),
                "После рестарта цель задания ещё не восстановлена источниками: перезапускать план вслепую "
                        + "нельзя — он встанет по умолчанию у точки сбора, а не на своём участке. Как только "
                        + "источники вернут цель, следующая сборка доски перезапустит задание (observe15 B2)"
                        + " Иначе тест decide_UnknownGenerationRunningWithoutTarget_StaysRunning перестанет ловить эту поломку. При смене decide_UnknownGenerationRunningWithoutTarget_StaysRunning проверь это утверждение.");
    }
}
