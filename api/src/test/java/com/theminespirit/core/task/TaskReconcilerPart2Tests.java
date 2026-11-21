package com.theminespirit.core.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.engine.RunProgress;
import com.theminespirit.core.engine.RunStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TaskReconcilerPart2Tests {
    private static final String BOT = "11111111-1111-1111-1111-111111111111";
    private static final RunProgress STEP_ONE = new RunProgress(1, 2, "goto", null, null);
    private static final RunProgress STEP_TWO = new RunProgress(2, 2, "gather", 3, 5);

    private TaskRegistry registry;
    private TaskReconciler reconciler;
    private String taskId;

    @BeforeEach
    void setUp() {
        registry = new TaskRegistry();
        reconciler = new TaskReconciler(registry);
        taskId = registry.reserve(BOT, "цель");
    }

    private TaskRecord record() {
        return registry.get(taskId).orElseThrow();
    }

    private void reconcile(SpiritReading reading) {
        reconciler.reconcile(record(), reading);
    }

    @Test
    void reconcile_SpiritTaskReplacedOutsideRest_ClosesTaskAsCancelledWithDisplacedReason() {
        registry.markStarted(taskId, 3);
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null).withGeneration(3));

        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null).withGeneration(4));

        assertEquals(
                TaskState.CANCELLED,
                record().state(),
                "Задача духа заменена командой или планировщиком: REST-задача обязана закрыться, а не висеть "
                        + "running над чужой работой Иначе тест reconcile_SpiritTaskReplacedOutsideRest_ClosesTaskAsCancelledWithDisplacedReason перестанет ловить эту поломку. При смене reconcile_SpiritTaskReplacedOutsideRest_ClosesTaskAsCancelledWithDisplacedReason проверь это утверждение.");
        assertTrue(
                record().error().contains("вытеснена вне REST"),
                "Клиент обязан увидеть причину закрытия: вытеснена вне REST; получено  Иначе тест reconcile_SpiritTaskReplacedOutsideRest_ClosesTaskAsCancelledWithDisplacedReason перестанет ловить эту поломку. При смене reconcile_SpiritTaskReplacedOutsideRest_ClosesTaskAsCancelledWithDisplacedReason проверь это утверждение."
                        + record().error());
    }

    @Test
    void reconcile_SpiritTaskReplacedAndAlreadyFinished_ClosesTaskAsCancelledNotSucceeded() {
        registry.markStarted(taskId, 3);

        reconcile(SpiritReading.living(RunStatus.SUCCEEDED, null, null).withGeneration(4));

        assertEquals(
                TaskState.CANCELLED,
                record().state(),
                "Успех чужой задачи не может засчитываться REST-задаче: поколение не совпало Иначе тест reconcile_SpiritTaskReplacedAndAlreadyFinished_ClosesTaskAsCancelledNotSucceeded перестанет ловить эту поломку. При смене reconcile_SpiritTaskReplacedAndAlreadyFinished_ClosesTaskAsCancelledNotSucceeded проверь это утверждение.");
    }

    @Test
    void reconcile_SameGeneration_KeepsTaskRunning() {
        registry.markStarted(taskId, 3);

        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null).withGeneration(3));
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_TWO, null).withGeneration(3));

        assertEquals(
                TaskState.RUNNING,
                record().state(),
                "Поколение совпало: задача та же и продолжает идти Иначе тест reconcile_SameGeneration_KeepsTaskRunning перестанет ловить эту поломку. При смене reconcile_SameGeneration_KeepsTaskRunning проверь это утверждение.");
    }

    @Test
    void reconcile_SpiritRestoredWithUnknownGeneration_KeepsTaskRunning() {
        registry.markStarted(taskId, 3);

        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null)
                .withGeneration(SpiritReading.UNKNOWN_GENERATION));

        assertEquals(
                TaskState.RUNNING,
                record().state(),
                "После перезагрузки духа поколение неизвестно: это та же задача из снимка, а не вытеснение Иначе тест reconcile_SpiritRestoredWithUnknownGeneration_KeepsTaskRunning перестанет ловить эту поломку. При смене reconcile_SpiritRestoredWithUnknownGeneration_KeepsTaskRunning проверь это утверждение.");
    }

    @Test
    void forgetUnobserved_UnobservedTask_DropsItsAbsenceCounter() {
        for (int i = 0; i < TaskReconciler.ABSENT_GRACE_TICKS - 1; i++) {
            reconcile(SpiritReading.absent());
        }

        reconciler.forgetUnobserved(List.of());
        reconcile(SpiritReading.absent());

        assertEquals(
                TaskState.ACCEPTED,
                record().state(),
                "Задача, переставшая наблюдаться, начинает льготу заново: иначе её счётчик отсутствия жил бы "
                        + "до перезапуска сервера и задача закрылась бы по чужому отсутствию При смене forgetUnobserved_UnobservedTask_DropsItsAbsenceCounter проверь это утверждение.");
    }

    @Test
    void forgetUnobserved_TaskStillObserved_KeepsItsAbsenceCounter() {
        for (int i = 0; i < TaskReconciler.ABSENT_GRACE_TICKS - 1; i++) {
            reconcile(SpiritReading.absent());
        }

        reconciler.forgetUnobserved(List.of(record()));
        reconcile(SpiritReading.absent());

        assertEquals(
                TaskState.FAILED,
                record().state(),
                "Наблюдаемая задача сохраняет счётчик: сотое отсутствие закрывает её, а не начинает льготу заново Иначе тест forgetUnobserved_TaskStillObserved_KeepsItsAbsenceCounter перестанет ловить эту поломку. При смене forgetUnobserved_TaskStillObserved_KeepsItsAbsenceCounter проверь это утверждение.");
    }

    @Test
    void reconcile_CancelledOutsideRest_ClosesTaskAsCancelled() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));

        reconcile(SpiritReading.living(RunStatus.CANCELLED, null, null));

        assertEquals(
                TaskState.CANCELLED,
                record().state(),
                "Отмена задачи у духа обязана закрывать REST-задачу Иначе тест reconcile_CancelledOutsideRest_ClosesTaskAsCancelled перестанет ловить эту поломку. При смене reconcile_CancelledOutsideRest_ClosesTaskAsCancelled проверь это утверждение.");
    }

    @Test
    void reconcile_SpiritWithoutTaskStatus_KeepsAccepted() {
        reconcile(SpiritReading.living(null, null, null));

        assertEquals(
                TaskState.ACCEPTED,
                record().state(),
                "Пока дух не отдал статус задачи (восстановление), запись остаётся accepted Иначе тест reconcile_SpiritWithoutTaskStatus_KeepsAccepted перестанет ловить эту поломку. При смене reconcile_SpiritWithoutTaskStatus_KeepsAccepted проверь это утверждение.");
    }

    @Test
    void reconcile_DeadSpirit_FailsImmediatelyWithGoneReason() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));

        reconcile(SpiritReading.dead());

        assertEquals(
                TaskState.FAILED,
                record().state(),
                "Погибший дух обязан проваливать задачу сразу Иначе тест reconcile_DeadSpirit_FailsImmediatelyWithGoneReason перестанет ловить эту поломку. При смене reconcile_DeadSpirit_FailsImmediatelyWithGoneReason проверь это утверждение.");
        assertTrue(
                record().error().startsWith("дух исчез"),
                "Причина обязана начинаться с «дух исчез»:  Иначе тест reconcile_DeadSpirit_FailsImmediatelyWithGoneReason перестанет ловить эту поломку. При смене reconcile_DeadSpirit_FailsImmediatelyWithGoneReason проверь это утверждение."
                        + record().error());
    }

    @Test
    void reconcile_AbsentBelowGrace_KeepsRunning() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));

        for (int tick = 0; tick < TaskReconciler.ABSENT_GRACE_TICKS - 1; tick++) {
            reconcile(SpiritReading.absent());
        }

        assertEquals(
                TaskState.RUNNING,
                record().state(),
                "Дух, которого нет меньше " + TaskReconciler.ABSENT_GRACE_TICKS
                        + " тиков (перезагрузка чанка), не должен проваливать задачу Иначе тест reconcile_AbsentBelowGrace_KeepsRunning перестанет ловить эту поломку. При смене reconcile_AbsentBelowGrace_KeepsRunning проверь это утверждение.");
    }

    @Test
    void reconcile_AbsentForGraceTicks_FailsWithGoneReason() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));

        for (int tick = 0; tick < TaskReconciler.ABSENT_GRACE_TICKS; tick++) {
            reconcile(SpiritReading.absent());
        }

        assertEquals(
                TaskState.FAILED,
                record().state(),
                "Дух, не вернувшийся за " + TaskReconciler.ABSENT_GRACE_TICKS
                        + " тиков, обязан провалить задачу Иначе тест reconcile_AbsentForGraceTicks_FailsWithGoneReason перестанет ловить эту поломку. При смене reconcile_AbsentForGraceTicks_FailsWithGoneReason проверь это утверждение.");
        assertTrue(
                record().error().startsWith("дух исчез"),
                "Причина обязана начинаться с «дух исчез»:  Иначе тест reconcile_AbsentForGraceTicks_FailsWithGoneReason перестанет ловить эту поломку. При смене reconcile_AbsentForGraceTicks_FailsWithGoneReason проверь это утверждение."
                        + record().error());
    }

    @Test
    void reconcile_SpiritReturnsBeforeGrace_ResetsAbsenceCounter() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));
        for (int tick = 0; tick < TaskReconciler.ABSENT_GRACE_TICKS - 1; tick++) {
            reconcile(SpiritReading.absent());
        }
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));

        for (int tick = 0; tick < TaskReconciler.ABSENT_GRACE_TICKS - 1; tick++) {
            reconcile(SpiritReading.absent());
        }

        assertEquals(
                TaskState.RUNNING,
                record().state(),
                "Возвращение духа обязано обнулять счётчик отсутствия: иначе короткие перезагрузки копятся в провал При смене reconcile_SpiritReturnsBeforeGrace_ResetsAbsenceCounter проверь это утверждение.");
    }

    @ParameterizedTest
    @EnumSource(value = RunStatus.class)
    void reconcile_TaskCancelledConcurrently_IgnoresStaleReadingWithoutThrowing(RunStatus status) {
        TaskRecord stale = record();
        registry.observe(taskId, TaskState.CANCELLED, null, null);

        reconciler.reconcile(stale, SpiritReading.living(status, STEP_ONE, "причина"));

        assertEquals(
                TaskState.CANCELLED,
                record().state(),
                "DELETE из HTTP-потока выигрывает у устаревшего чтения watcher: терминальную задачу не трогаем Иначе тест reconcile_TaskCancelledConcurrently_IgnoresStaleReadingWithoutThrowing перестанет ловить эту поломку. При смене reconcile_TaskCancelledConcurrently_IgnoresStaleReadingWithoutThrowing проверь это утверждение.");
    }
}
