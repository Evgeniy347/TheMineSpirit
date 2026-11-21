package com.theminespirit.core.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.engine.RunProgress;
import com.theminespirit.core.engine.RunStatus;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

        assertEquals(
                TaskState.RUNNING,
                record().state(),
                "Первый тик живого духа с задачей переводит accepted в running: клиент видит старт Иначе тест reconcile_RunningSpirit_MovesAcceptedToRunningWithProgress перестанет ловить эту поломку. При смене reconcile_RunningSpirit_MovesAcceptedToRunningWithProgress проверь это утверждение.");
        assertEquals(
                STEP_ONE,
                record().progress(),
                "Прогресс текущего скрипта обязан попасть в запись задачи Иначе тест reconcile_RunningSpirit_MovesAcceptedToRunningWithProgress перестанет ловить эту поломку. При смене reconcile_RunningSpirit_MovesAcceptedToRunningWithProgress проверь это утверждение.");
    }

    @Test
    void reconcile_ProgressChange_AppendsOneProgressEventPerChange() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));
        long before = registry.log(taskId).lastId();

        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_TWO, null));

        assertEquals(
                before + 1,
                registry.log(taskId).lastId(),
                "Смена шага и счётчика обязана дать ровно одно событие progress Иначе тест reconcile_ProgressChange_AppendsOneProgressEventPerChange перестанет ловить эту поломку. При смене reconcile_ProgressChange_AppendsOneProgressEventPerChange проверь это утверждение.");
        assertEquals(
                STEP_TWO,
                record().progress(),
                "Запись обязана хранить последний прогресс Иначе тест reconcile_ProgressChange_AppendsOneProgressEventPerChange перестанет ловить эту поломку. При смене reconcile_ProgressChange_AppendsOneProgressEventPerChange проверь это утверждение.");
    }

    @Test
    void reconcile_ProgressBetweenStepsWithoutScriptName_KeepsPreviousProgressAndAppendsNoEvent() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));
        long before = registry.log(taskId).lastId();

        reconcile(SpiritReading.living(RunStatus.RUNNING, new RunProgress(2, 2, null, null, null), null));

        assertEquals(
                before,
                registry.log(taskId).lastId(),
                "Момент между шагами sequence, когда следующий скрипт ещё не создан, не должен давать событие progress "
                        + "с current_script=null: клиент увидит шаг вместе с именем скрипта Иначе тест reconcile_ProgressBetweenStepsWithoutScriptName_KeepsPreviousProgressAndAppendsNoEvent перестанет ловить эту поломку. При смене reconcile_ProgressBetweenStepsWithoutScriptName_KeepsPreviousProgressAndAppendsNoEvent проверь это утверждение.");
        assertEquals(
                STEP_ONE,
                record().progress(),
                "Запись обязана хранить последний прогресс с именем скрипта Иначе тест reconcile_ProgressBetweenStepsWithoutScriptName_KeepsPreviousProgressAndAppendsNoEvent перестанет ловить эту поломку. При смене reconcile_ProgressBetweenStepsWithoutScriptName_KeepsPreviousProgressAndAppendsNoEvent проверь это утверждение.");
    }

    @Test
    void reconcile_Succeeded_FinishesKeepingLastProgress() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_TWO, null));

        reconcile(SpiritReading.living(RunStatus.SUCCEEDED, null, null));

        assertEquals(
                TaskState.SUCCEEDED,
                record().state(),
                "Успех духа обязан закрывать задачу как succeeded Иначе тест reconcile_Succeeded_FinishesKeepingLastProgress перестанет ловить эту поломку. При смене reconcile_Succeeded_FinishesKeepingLastProgress проверь это утверждение.");
        assertEquals(
                STEP_TWO,
                record().progress(),
                "У завершённой задачи остаётся последний прогресс: по нему видно, где закончилось Иначе тест reconcile_Succeeded_FinishesKeepingLastProgress перестанет ловить эту поломку. При смене reconcile_Succeeded_FinishesKeepingLastProgress проверь это утверждение.");
        assertNull(
                record().error(),
                "У успешной задачи ошибки нет Иначе тест reconcile_Succeeded_FinishesKeepingLastProgress перестанет ловить эту поломку. При смене reconcile_Succeeded_FinishesKeepingLastProgress проверь это утверждение.");
    }

    @Test
    void reconcile_AcceptedThenInstantSuccess_FinishesWithoutRunningStep() {
        reconcile(SpiritReading.living(RunStatus.SUCCEEDED, null, null));

        assertEquals(
                TaskState.SUCCEEDED,
                record().state(),
                "Задача, завершившаяся до первого опроса, обязана закрыться из accepted Иначе тест reconcile_AcceptedThenInstantSuccess_FinishesWithoutRunningStep перестанет ловить эту поломку. При смене reconcile_AcceptedThenInstantSuccess_FinishesWithoutRunningStep проверь это утверждение.");
    }

    @Test
    void reconcile_SucceededWithResult_StoresResultInTaskRecord() {
        Object result = Map.of("chopped", 2);
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));

        reconcile(SpiritReading.living(RunStatus.SUCCEEDED, null, null, result));

        assertEquals(
                TaskState.SUCCEEDED,
                record().state(),
                "Успех духа обязан закрывать задачу как succeeded Иначе тест reconcile_SucceededWithResult_StoresResultInTaskRecord перестанет ловить эту поломку. При смене reconcile_SucceededWithResult_StoresResultInTaskRecord проверь это утверждение.");
        assertEquals(
                result,
                record().result(),
                "Итог успеха обязан попасть в запись задачи: по нему клиент узнаёт, что именно сделано Иначе тест reconcile_SucceededWithResult_StoresResultInTaskRecord перестанет ловить эту поломку. При смене reconcile_SucceededWithResult_StoresResultInTaskRecord проверь это утверждение.");
    }

    @Test
    void reconcile_SucceededWithoutResult_LeavesResultNull() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));

        reconcile(SpiritReading.living(RunStatus.SUCCEEDED, null, null));

        assertNull(
                record().result(),
                "Успех без итога обязан оставлять result == null Иначе тест reconcile_SucceededWithoutResult_LeavesResultNull перестанет ловить эту поломку. При смене reconcile_SucceededWithoutResult_LeavesResultNull проверь это утверждение.");
    }

    @Test
    void reconcile_FailedOrCancelled_LeavesResultNull() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));
        reconcile(SpiritReading.living(RunStatus.FAILED, null, "цель недостижима"));

        assertNull(
                record().result(),
                "Провал не несёт итога успеха: result обязан остаться null Иначе тест reconcile_FailedOrCancelled_LeavesResultNull перестанет ловить эту поломку. При смене reconcile_FailedOrCancelled_LeavesResultNull проверь это утверждение.");
    }

    @Test
    void reconcile_Cancelled_LeavesResultNull() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));
        reconcile(SpiritReading.living(RunStatus.CANCELLED, null, null));

        assertNull(
                record().result(),
                "Отмена не несёт итога успеха: result обязан остаться null Иначе тест reconcile_Cancelled_LeavesResultNull перестанет ловить эту поломку. При смене reconcile_Cancelled_LeavesResultNull проверь это утверждение.");
    }

    @Test
    void reconcile_Failed_StoresReasonAndScriptFailedEventNamesScript() {
        reconcile(SpiritReading.living(RunStatus.RUNNING, STEP_ONE, null));

        reconcile(SpiritReading.living(RunStatus.FAILED, null, "цель недостижима"));

        assertEquals(
                TaskState.FAILED,
                record().state(),
                "Провал скрипта обязан закрывать задачу как failed Иначе тест reconcile_Failed_StoresReasonAndScriptFailedEventNamesScript перестанет ловить эту поломку. При смене reconcile_Failed_StoresReasonAndScriptFailedEventNamesScript проверь это утверждение.");
        assertEquals(
                "цель недостижима",
                record().error(),
                "Причина провала духа обязана попасть в error Иначе тест reconcile_Failed_StoresReasonAndScriptFailedEventNamesScript перестанет ловить эту поломку. При смене reconcile_Failed_StoresReasonAndScriptFailedEventNamesScript проверь это утверждение.");
        TaskEventLog.Catchup events = registry.log(taskId).since(0);
        assertTrue(
                events.events().stream()
                        .anyMatch(event -> TaskEvent.TYPE_SCRIPT_FAILED.equals(event.type())
                                && "goto".equals(event.data().get("script"))
                                && "цель недостижима".equals(event.data().get("reason"))),
                "Провал обязан дать script_failed с именем скрипта goto и причиной:  Иначе тест reconcile_Failed_StoresReasonAndScriptFailedEventNamesScript перестанет ловить эту поломку. При смене reconcile_Failed_StoresReasonAndScriptFailedEventNamesScript проверь это утверждение."
                        + events.events());
    }

    @Test
    void reconcile_FailedOnFirstPoll_UsesFailedAtSoScriptFailedNamesScript() {
        RunProgress failedAt = new RunProgress(2, 3, "goto", null, null);

        reconcile(SpiritReading.failed("путь не найден", failedAt));

        assertEquals(
                TaskState.FAILED,
                record().state(),
                "Провал до первого опроса обязан закрыть задачу как failed Иначе тест reconcile_FailedOnFirstPoll_UsesFailedAtSoScriptFailedNamesScript перестанет ловить эту поломку. При смене reconcile_FailedOnFirstPoll_UsesFailedAtSoScriptFailedNamesScript проверь это утверждение.");
        assertEquals(
                failedAt,
                record().progress(),
                "Прогресс провалившейся задачи обязан показывать шаг и скрипт, на которых она упала Иначе тест reconcile_FailedOnFirstPoll_UsesFailedAtSoScriptFailedNamesScript перестанет ловить эту поломку. При смене reconcile_FailedOnFirstPoll_UsesFailedAtSoScriptFailedNamesScript проверь это утверждение.");
        TaskEventLog.Catchup events = registry.log(taskId).since(0);
        assertTrue(
                events.events().stream()
                        .anyMatch(event -> TaskEvent.TYPE_SCRIPT_FAILED.equals(event.type())
                                && "goto".equals(event.data().get("script"))
                                && "путь не найден".equals(event.data().get("reason"))),
                "script_failed обязан назвать скрипт goto, даже если наблюдатель не видел его выполняющимся:  Иначе тест reconcile_FailedOnFirstPoll_UsesFailedAtSoScriptFailedNamesScript перестанет ловить эту поломку. При смене reconcile_FailedOnFirstPoll_UsesFailedAtSoScriptFailedNamesScript проверь это утверждение."
                        + events.events());
    }

    @Test
    void reconcile_FailedWithoutReason_StillExplainsFailure() {
        reconcile(SpiritReading.living(RunStatus.FAILED, null, null));

        assertFalse(
                record().error() == null || record().error().isBlank(),
                "Провал без причины от движка не должен давать пустой error: клиенту нужно объяснение Иначе тест reconcile_FailedWithoutReason_StillExplainsFailure перестанет ловить эту поломку. При смене reconcile_FailedWithoutReason_StillExplainsFailure проверь это утверждение.");
    }
}
