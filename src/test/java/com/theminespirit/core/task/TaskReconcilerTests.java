package com.theminespirit.core.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.engine.RunProgress;
import com.theminespirit.core.engine.RunStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TaskReconcilerTests {
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
    void reconcile_RunningSpirit_MovesAcceptedToRunningWithProgress() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));

        assertEquals(TaskState.RUNNING, record().state(),
                "Первый тик живого духа с задачей переводит accepted в running: клиент видит старт");
        assertEquals(STEP_ONE, record().progress(), "Прогресс текущего скрипта обязан попасть в запись задачи");
    }

    @Test
    void reconcile_ProgressChange_AppendsOneProgressEventPerChange() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));
        long before = registry.appendedEventCount(taskId);

        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_TWO, null));

        assertEquals(before + 1, registry.appendedEventCount(taskId),
                "Смена шага и счётчика обязана дать ровно одно событие progress");
        assertEquals(STEP_TWO, record().progress(), "Запись обязана хранить последний прогресс");
    }


    @Test
    void reconcile_ProgressBetweenStepsWithoutScriptName_KeepsPreviousProgressAndAppendsNoEvent() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));
        long before = registry.appendedEventCount(taskId);

        reconcile(SpiritReading.living(RunStatus.RUNNING, new RunProgress(2, 2, null, null, null), null));

        assertEquals(before, registry.appendedEventCount(taskId),
                "Момент между шагами sequence, когда следующий скрипт ещё не создан, не должен давать событие progress "
                        + "с current_script=null: клиент увидит шаг вместе с именем скрипта");
        assertEquals(STEP_ONE, record().progress(), "Запись обязана хранить последний прогресс с именем скрипта");
    }
    @Test
    void reconcile_Succeeded_FinishesKeepingLastProgress() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_TWO, null));

        reconcile(SpiritReading.living(RunStatus.SUCCEEDED, null, null));

        assertEquals(TaskState.SUCCEEDED, record().state(), "Успех духа обязан закрывать задачу как succeeded");
        assertEquals(STEP_TWO, record().progress(),
                "У завершённой задачи остаётся последний прогресс: по нему видно, где закончилось");
        assertNull(record().error(), "У успешной задачи ошибки нет");
    }

    @Test
    void reconcile_AcceptedThenInstantSuccess_FinishesWithoutRunningStep() {
        reconcile(SpiritReading.living(RunStatus.SUCCEEDED, null, null));

        assertEquals(TaskState.SUCCEEDED, record().state(),
                "Задача, завершившаяся до первого опроса, обязана закрыться из accepted");
    }

    @Test
    void reconcile_Failed_StoresReasonAndScriptFailedEventNamesScript() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));

        reconcile(SpiritReading.living(RunStatus.FAILED, null, "цель недостижима"));

        assertEquals(TaskState.FAILED, record().state(), "Провал скрипта обязан закрывать задачу как failed");
        assertEquals("цель недостижима", record().error(), "Причина провала духа обязана попасть в error");
        TaskEventLog.Catchup events = registry.log(taskId).since(0);
        assertTrue(events.events().stream().anyMatch(event -> event.type().equals(TaskEvent.TYPE_SCRIPT_FAILED)
                && "goto".equals(event.data().get("script")) && "цель недостижима".equals(event.data().get("reason"))),
                "Провал обязан дать script_failed с именем скрипта goto и причиной: " + events.events());
    }


    @Test
    void reconcile_FailedOnFirstPoll_UsesFailedAtSoScriptFailedNamesScript() {
        RunProgress failedAt = new RunProgress(2, 3, "goto", null, null);

        reconcile(SpiritReading.failed("путь не найден", failedAt));

        assertEquals(TaskState.FAILED, record().state(), "Провал до первого опроса обязан закрыть задачу как failed");
        assertEquals(failedAt, record().progress(),
                "Прогресс провалившейся задачи обязан показывать шаг и скрипт, на которых она упала");
        TaskEventLog.Catchup events = registry.log(taskId).since(0);
        assertTrue(events.events().stream().anyMatch(event -> event.type().equals(TaskEvent.TYPE_SCRIPT_FAILED)
                && "goto".equals(event.data().get("script")) && "путь не найден".equals(event.data().get("reason"))),
                "script_failed обязан назвать скрипт goto, даже если наблюдатель не видел его выполняющимся: "
                        + events.events());
    }
    @Test
    void reconcile_FailedWithoutReason_StillExplainsFailure() {
        reconcile(SpiritReading.living(RunStatus.FAILED, null, null));

        assertFalse(record().error() == null || record().error().isBlank(),
                "Провал без причины от движка не должен давать пустой error: клиенту нужно объяснение");
    }

    @Test
    void reconcile_CancelledOutsideRest_ClosesTaskAsCancelled() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));

        reconcile(SpiritReading.living(RunStatus.CANCELLED, null, null));

        assertEquals(TaskState.CANCELLED, record().state(), "Отмена задачи у духа обязана закрывать REST-задачу");
    }

    @Test
    void reconcile_SpiritWithoutTaskStatus_KeepsAccepted() {
        reconcile(SpiritReading.living(null, null, null));

        assertEquals(TaskState.ACCEPTED, record().state(),
                "Пока дух не отдал статус задачи (восстановление), запись остаётся accepted");
    }

    @Test
    void reconcile_DeadSpirit_FailsImmediatelyWithGoneReason() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));

        reconcile(SpiritReading.dead());

        assertEquals(TaskState.FAILED, record().state(), "Погибший дух обязан проваливать задачу сразу");
        assertTrue(record().error().startsWith("дух исчез"),
                "Причина обязана начинаться с «дух исчез»: " + record().error());
    }

    @Test
    void reconcile_AbsentBelowGrace_KeepsRunning() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));

        for (int tick = 0; tick < TaskReconciler.ABSENT_GRACE_TICKS - 1; tick++) {
            reconcile(SpiritReading.absent());
        }

        assertEquals(TaskState.RUNNING, record().state(),
                "Дух, которого нет меньше " + TaskReconciler.ABSENT_GRACE_TICKS
                        + " тиков (перезагрузка чанка), не должен проваливать задачу");
    }

    @Test
    void reconcile_AbsentForGraceTicks_FailsWithGoneReason() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));

        for (int tick = 0; tick < TaskReconciler.ABSENT_GRACE_TICKS; tick++) {
            reconcile(SpiritReading.absent());
        }

        assertEquals(TaskState.FAILED, record().state(),
                "Дух, не вернувшийся за " + TaskReconciler.ABSENT_GRACE_TICKS + " тиков, обязан провалить задачу");
        assertTrue(record().error().startsWith("дух исчез"),
                "Причина обязана начинаться с «дух исчез»: " + record().error());
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

        assertEquals(TaskState.RUNNING, record().state(),
                "Возвращение духа обязано обнулять счётчик отсутствия: иначе короткие перезагрузки копятся в провал");
    }

    @ParameterizedTest
    @EnumSource(value = RunStatus.class)
    void reconcile_TaskCancelledConcurrently_IgnoresStaleReadingWithoutThrowing(RunStatus status) {
        TaskRecord stale = record();
        registry.observe(taskId, TaskState.CANCELLED, null, null);

        reconciler.reconcile(stale, SpiritReading.living(status, STEP_ONE, "причина"));

        assertEquals(TaskState.CANCELLED, record().state(),
                "DELETE из HTTP-потока выигрывает у устаревшего чтения watcher: терминальную задачу не трогаем");
    }
}
