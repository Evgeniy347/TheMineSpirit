package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.task.SpiritReading;
import org.junit.jupiter.api.Test;

class TicketRunTests {

    @Test
    void decide_OwnTaskSucceeded_Succeeded() {
        assertEquals(TicketRun.SUCCEEDED, TicketRun.decide(5, 5, RunStatus.SUCCEEDED, false), "Своя задача закрыла");
    }

    @Test
    void decide_ForeignTaskFailed_ResumeNotFailed() {
        assertEquals(
                TicketRun.RESUME,
                TicketRun.decide(5, 6, RunStatus.FAILED, false),
                "Провал чужой задачи (фоновой еды, рефлекса) заданию доски не приписывается (INC-31/24)");
    }

    @Test
    void decide_ForeignTaskSucceeded_ResumeNotDone() {
        assertEquals(
                TicketRun.RESUME,
                TicketRun.decide(5, 6, RunStatus.SUCCEEDED, false),
                "Успех чужой задачи не закрывает задание: дух продолжает своё");
    }

    @Test
    void decide_ForeignTaskRunning_Displaced() {
        assertEquals(TicketRun.DISPLACED, TicketRun.decide(5, 6, RunStatus.RUNNING, false), "Чужая задача идёт");
    }

    @Test
    void decide_CombatAfterForeignTask_Displaced() {
        assertEquals(
                TicketRun.DISPLACED,
                TicketRun.decide(5, 6, RunStatus.SUCCEEDED, true),
                "Пока бой держит духа, задание ждёт его, а не перезапускается");
    }

    @Test
    void decide_OwnTaskCancelledAfterCombat_Resume() {
        assertEquals(TicketRun.RESUME, TicketRun.decide(5, 5, RunStatus.CANCELLED, false), "Бой снял — продолжить");
    }

    @Test
    void decide_UnknownGeneration_SettlesByStatus() {
        long unknown = SpiritReading.UNKNOWN_GENERATION;

        assertEquals(TicketRun.FAILED, TicketRun.decide(unknown, 6, RunStatus.FAILED, false), "После рестарта");
        assertEquals(TicketRun.RUNNING, TicketRun.decide(unknown, 6, RunStatus.CANCELLED, false), "Без поколения");
    }

    @Test
    void decide_UnknownGenerationRunning_ResumeOwnPlan() {
        long unknown = SpiritReading.UNKNOWN_GENERATION;

        assertEquals(
                TicketRun.RESUME,
                TicketRun.decide(unknown, 6, RunStatus.RUNNING, false),
                "После рестарта поколение задания неизвестно, а дух может выполнять чужой фоновый скрипт: "
                        + "прогресс такого скрипта заданию не принадлежит, поэтому задание заново стартует свой план "
                        + "(observe15 B2), иначе сторож прогресса берёт чужой RunProgress");
    }

    @Test
    void decide_UnknownGenerationRunningWithoutTarget_StaysRunning() {
        long unknown = SpiritReading.UNKNOWN_GENERATION;

        assertEquals(
                TicketRun.RUNNING,
                TicketRun.decide(unknown, 6, RunStatus.RUNNING, false, false),
                "После рестарта цель задания ещё не восстановлена источниками: перезапускать план вслепую "
                        + "нельзя — он встанет по умолчанию у точки сбора, а не на своём участке. Как только "
                        + "источники вернут цель, следующая сборка доски перезапустит задание (observe15 B2)");
    }
}
