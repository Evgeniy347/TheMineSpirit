package com.theminespirit.core.engine;

import static com.theminespirit.core.engine.TaskRunnerTestsSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ScriptCall;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TaskRunnerPart5Tests {
    @Test
    void cancel_AfterFirstStepCompleted_CancelsOnlyLiveScript() {
        AtomicInteger completedCancels = new AtomicInteger();
        AtomicInteger runningCancels = new AtomicInteger();
        ScriptRegistry registry = ScriptRegistry.of(Map.of(
                "first",
                        params -> new ScriptInstance() {
                            @Override
                            public ScriptStatus tick() {
                                return ScriptStatus.SUCCEEDED;
                            }

                            @Override
                            public void cancel() {
                                completedCancels.incrementAndGet();
                            }
                        },
                "second",
                        params -> new ScriptInstance() {
                            @Override
                            public ScriptStatus tick() {
                                return ScriptStatus.RUNNING;
                            }

                            @Override
                            public void cancel() {
                                runningCancels.incrementAndGet();
                            }
                        }));
        TaskRunner runner = TaskRunner.start(
                sequence(script("first"), script("second")), registry, TestScripts.condition(CHECK, true));
        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "после успеха первого шага sequence продолжает второй Иначе тест cancel_AfterFirstStepCompleted_CancelsOnlyLiveScript перестанет ловить эту поломку. При смене cancel_AfterFirstStepCompleted_CancelsOnlyLiveScript проверь это утверждение.");
        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "второй шаг обязан встать в работу и держать узел живым Иначе тест cancel_AfterFirstStepCompleted_CancelsOnlyLiveScript перестанет ловить эту поломку. При смене cancel_AfterFirstStepCompleted_CancelsOnlyLiveScript проверь это утверждение.");
        runner.cancel();
        assertEquals(
                0,
                completedCancels.get(),
                "завершённый скрипт не получает cancel: иначе он освободил бы ресурсы, уже переданные дальше При смене cancel_AfterFirstStepCompleted_CancelsOnlyLiveScript проверь это утверждение.");
        assertEquals(
                1,
                runningCancels.get(),
                "cancel обязан дойти до работающего второго шага ровно один раз Иначе тест cancel_AfterFirstStepCompleted_CancelsOnlyLiveScript перестанет ловить эту поломку. При смене cancel_AfterFirstStepCompleted_CancelsOnlyLiveScript проверь это утверждение.");
    }

    @Test
    void cancel_RunningRun_TransitionsToCancelledAndFreezesStatus() {
        TaskRunner runner = TaskRunner.start(
                script(SCRIPT),
                TestScripts.singleRegistry(SCRIPT, TestScripts.succeeding(5)),
                TestScripts.condition(CHECK, true));
        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "перед отменой задача обязана быть в работе Иначе тест cancel_RunningRun_TransitionsToCancelledAndFreezesStatus перестанет ловить эту поломку. При смене cancel_RunningRun_TransitionsToCancelledAndFreezesStatus проверь это утверждение.");
        runner.cancel();
        assertEquals(
                RunStatus.CANCELLED,
                runner.status(),
                "cancel обязан перевести задачу в CANCELLED Иначе тест cancel_RunningRun_TransitionsToCancelledAndFreezesStatus перестанет ловить эту поломку. При смене cancel_RunningRun_TransitionsToCancelledAndFreezesStatus проверь это утверждение.");
        assertEquals(
                RunStatus.CANCELLED,
                runner.tick(),
                "после отмены tick не меняет статус Иначе тест cancel_RunningRun_TransitionsToCancelledAndFreezesStatus перестанет ловить эту поломку. При смене cancel_RunningRun_TransitionsToCancelledAndFreezesStatus проверь это утверждение.");
        assertTrue(
                runner.currentScript().isEmpty(),
                "после отмены нет текущего скрипта Иначе тест cancel_RunningRun_TransitionsToCancelledAndFreezesStatus перестанет ловить эту поломку. При смене cancel_RunningRun_TransitionsToCancelledAndFreezesStatus проверь это утверждение.");
    }

    @Test
    void tick_AfterTerminalStatus_DoesNotChangeStatus() {
        AtomicInteger creations = new AtomicInteger();
        ScriptFactory flip =
                params -> creations.incrementAndGet() == 1 ? new SucceedingInstance() : new FailingInstance();
        TaskRunner runner = TaskRunner.start(
                script(SCRIPT), ScriptRegistry.of(Map.of(SCRIPT, flip)), TestScripts.condition(CHECK, true));
        assertEquals(
                RunStatus.SUCCEEDED,
                runner.tick(),
                "первый тик доводит успешный скрипт до терминала Иначе тест tick_AfterTerminalStatus_DoesNotChangeStatus перестанет ловить эту поломку. При смене tick_AfterTerminalStatus_DoesNotChangeStatus проверь это утверждение.");
        assertEquals(
                RunStatus.SUCCEEDED,
                runner.tick(),
                "после терминала tick обязан возвращать тот же статус: повторный tick не должен перезапускать скрипт Иначе тест tick_AfterTerminalStatus_DoesNotChangeStatus перестанет ловить эту поломку. При смене tick_AfterTerminalStatus_DoesNotChangeStatus проверь это утверждение.");
        assertEquals(
                1,
                creations.get(),
                "после терминала движок не должен создавать новый экземпляр скрипта Иначе тест tick_AfterTerminalStatus_DoesNotChangeStatus перестанет ловить эту поломку. При смене tick_AfterTerminalStatus_DoesNotChangeStatus проверь это утверждение.");
    }

    @Test
    void currentScript_WhileRunning_ReportsActiveScriptAndClearsAfterTerminal() {
        TaskRunner runner = TaskRunner.start(
                script(SCRIPT),
                TestScripts.singleRegistry(SCRIPT, TestScripts.succeeding(2)),
                TestScripts.condition(CHECK, true));
        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "скрипт в два тика ещё работает Иначе тест currentScript_WhileRunning_ReportsActiveScriptAndClearsAfterTerminal перестанет ловить эту поломку. При смене currentScript_WhileRunning_ReportsActiveScriptAndClearsAfterTerminal проверь это утверждение.");
        assertEquals(
                SCRIPT,
                runner.currentScript().orElse(null),
                "пока скрипт исполняется, currentScript обязан называть его id: по нему bridge показывает прогресс Иначе тест currentScript_WhileRunning_ReportsActiveScriptAndClearsAfterTerminal перестанет ловить эту поломку. При смене currentScript_WhileRunning_ReportsActiveScriptAndClearsAfterTerminal проверь это утверждение.");
        assertEquals(
                RunStatus.SUCCEEDED,
                runner.tick(),
                "второй тик завершает скрипт Иначе тест currentScript_WhileRunning_ReportsActiveScriptAndClearsAfterTerminal перестанет ловить эту поломку. При смене currentScript_WhileRunning_ReportsActiveScriptAndClearsAfterTerminal проверь это утверждение.");
        assertTrue(
                runner.currentScript().isEmpty(),
                "после завершения текущего скрипта нет Иначе тест currentScript_WhileRunning_ReportsActiveScriptAndClearsAfterTerminal перестанет ловить эту поломку. При смене currentScript_WhileRunning_ReportsActiveScriptAndClearsAfterTerminal проверь это утверждение.");
    }

    @Test
    void start_NullArgument_IsRejected() {
        NullPointerException planError = assertThrows(
                NullPointerException.class,
                () -> TaskRunner.start(null, ScriptRegistry.of(Map.of()), ConditionRegistry.of(Map.of())),
                "start с null-планом обязан отказывать: без дерева задач исполнять нечего Иначе тест start_NullArgument_IsRejected перестанет ловить эту поломку. При смене start_NullArgument_IsRejected проверь это утверждение.");
        assertTrue(
                planError.getMessage().contains("plan"),
                "отказ за null-план обязан называть поле 'plan': по сообщению видно, что именно не задано. "
                        + "Фактическое:  Иначе тест start_NullArgument_IsRejected перестанет ловить эту поломку. При смене start_NullArgument_IsRejected проверь это утверждение."
                        + planError.getMessage());
        assertThrows(
                NullPointerException.class,
                () -> TaskRunner.start(script(SCRIPT), null, ConditionRegistry.of(Map.of())),
                "start с null-реестром скриптов обязан отказывать: движку неоткуда взять реализации Иначе тест start_NullArgument_IsRejected перестанет ловить эту поломку. При смене start_NullArgument_IsRejected проверь это утверждение.");
        assertThrows(
                NullPointerException.class,
                () -> TaskRunner.start(script(SCRIPT), ScriptRegistry.of(Map.of()), null),
                "start с null-реестром условий обязан отказывать: условие не сможет выбрать ветку При смене start_NullArgument_IsRejected проверь это утверждение.");
    }

    @Test
    void failureReason_BeforeFailure_IsEmpty() {
        TaskRunner runner = TaskRunner.start(
                script(SCRIPT),
                TestScripts.singleRegistry(SCRIPT, TestScripts.succeeding(1)),
                TestScripts.condition(CHECK, true));
        assertTrue(
                runner.failureReason().isEmpty(),
                "до провала причина отсутствует: пустой Optional отличает 'нет ошибки' от 'ошибка без текста' Иначе тест failureReason_BeforeFailure_IsEmpty перестанет ловить эту поломку. При смене failureReason_BeforeFailure_IsEmpty проверь это утверждение.");
    }

    @Test
    void script_FactoryReceivesParams_FromPlanNode() {
        Map<String, Object> params = Map.of("ticks", 4);
        TaskRunner runner = TaskRunner.start(
                new ScriptCall(SCRIPT, params),
                ScriptRegistry.of(Map.of(SCRIPT, received -> {
                    assertEquals(
                            params,
                            received,
                            "фабрика обязана получить параметры узла без потерь Иначе тест script_FactoryReceivesParams_FromPlanNode перестанет ловить эту поломку. При смене script_FactoryReceivesParams_FromPlanNode проверь это утверждение.");
                    return new SucceedingInstance();
                })),
                TestScripts.condition(CHECK, true));
        assertEquals(
                RunStatus.SUCCEEDED,
                runner.tick(),
                "фабрика, проверившая параметры, возвращает мгновенно успешный экземпляр Иначе тест script_FactoryReceivesParams_FromPlanNode перестанет ловить эту поломку. При смене script_FactoryReceivesParams_FromPlanNode проверь это утверждение.");
    }

    @Test
    void successResult_TerminatedWithResultScript_ReportsTextResult() {
        ScriptResult expected = new ScriptResult.Text("срублено 1 из 1");
        TaskRunner runner = TaskRunner.start(
                sequence(script(SCRIPT)),
                ScriptRegistry.of(Map.of(SCRIPT, TestScripts.resulting(1, expected))),
                TestScripts.condition(CHECK, true));
        assertEquals(
                RunStatus.SUCCEEDED,
                runner.tick(),
                "скрипт с итогом завершается успехом за один тик Иначе тест successResult_TerminatedWithResultScript_ReportsTextResult перестанет ловить эту поломку. При смене successResult_TerminatedWithResultScript_ReportsTextResult проверь это утверждение.");
        assertTrue(
                runner.successResult().isPresent(),
                "успех со скриптом-итогом обязан нести итог: без него стратег не узнает, что именно сделано Иначе тест successResult_TerminatedWithResultScript_ReportsTextResult перестанет ловить эту поломку. При смене successResult_TerminatedWithResultScript_ReportsTextResult проверь это утверждение.");
        assertEquals(
                expected,
                runner.successResult().orElse(null),
                "successResult обязан возвращать итог скрипта без искажений Иначе тест successResult_TerminatedWithResultScript_ReportsTextResult перестанет ловить эту поломку. При смене successResult_TerminatedWithResultScript_ReportsTextResult проверь это утверждение.");
    }

    @Test
    void successResult_ScriptWithoutResult_IsEmpty() {
        TaskRunner runner = TaskRunner.start(
                script(SCRIPT),
                TestScripts.singleRegistry(SCRIPT, TestScripts.succeeding(1)),
                TestScripts.condition(CHECK, true));
        assertEquals(
                RunStatus.SUCCEEDED,
                runner.tick(),
                "скрипт без итога завершается успехом Иначе тест successResult_ScriptWithoutResult_IsEmpty перестанет ловить эту поломку. При смене successResult_ScriptWithoutResult_IsEmpty проверь это утверждение.");
        assertTrue(
                runner.successResult().isEmpty(),
                "успех без итога обязан давать пустой Optional: null внутри Optional скрыл бы отсутствие данных Иначе тест successResult_ScriptWithoutResult_IsEmpty перестанет ловить эту поломку. При смене successResult_ScriptWithoutResult_IsEmpty проверь это утверждение.");
    }

    @Test
    void successResult_AfterCancelOfSucceededTask_ResultIsPreserved() {
        TaskRunner runner = TaskRunner.start(
                script(SCRIPT),
                ScriptRegistry.of(Map.of(SCRIPT, TestScripts.resulting(1, new ScriptResult.Text("готово")))),
                TestScripts.condition(CHECK, true));
        assertEquals(
                RunStatus.SUCCEEDED,
                runner.tick(),
                "задача завершается успехом Иначе тест successResult_AfterCancelOfSucceededTask_ResultIsPreserved перестанет ловить эту поломку. При смене successResult_AfterCancelOfSucceededTask_ResultIsPreserved проверь это утверждение.");
        runner.cancel();
        assertEquals(
                RunStatus.SUCCEEDED,
                runner.status(),
                "cancel после успеха не меняет терминальный статус Иначе тест successResult_AfterCancelOfSucceededTask_ResultIsPreserved перестанет ловить эту поломку. При смене successResult_AfterCancelOfSucceededTask_ResultIsPreserved проверь это утверждение.");
        assertTrue(
                runner.successResult().isPresent(),
                "cancel после успеха обязан сохранять итог: отчёт задачи теряет данные итога при вызове cancel Иначе тест successResult_AfterCancelOfSucceededTask_ResultIsPreserved перестанет ловить эту поломку. При смене successResult_AfterCancelOfSucceededTask_ResultIsPreserved проверь это утверждение.");
    }

    @Test
    void snapshot_TerminatedRunner_IsEmpty() {
        TaskRunner runner = TaskRunner.start(
                script(SCRIPT),
                TestScripts.singleRegistry(SCRIPT, TestScripts.succeeding(1)),
                TestScripts.condition(CHECK, true));
        assertEquals(
                RunStatus.SUCCEEDED,
                runner.tick(),
                "задача завершается успехом Иначе тест snapshot_TerminatedRunner_IsEmpty перестанет ловить эту поломку. При смене snapshot_TerminatedRunner_IsEmpty проверь это утверждение.");
        assertTrue(
                runner.snapshot().isEmpty(),
                "завершённая задача не имеет снимка: encodeForSave для не-RUNNING получает Optional.empty и не "
                        + "пишет итог в снимок — итог живёт отдельно Иначе тест snapshot_TerminatedRunner_IsEmpty перестанет ловить эту поломку. При смене snapshot_TerminatedRunner_IsEmpty проверь это утверждение.");
    }
}
