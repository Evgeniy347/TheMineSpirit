package com.theminespirit.core.planner;

import static com.theminespirit.core.planner.AcquireScriptTestsSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.script.api.ConditionCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AcquireScriptPart2Tests {
    @Test
    void subplanExceedsBudget_NodesOverBudget_BuildFailsWithNumbers() {
        StubSupplies supplies = new StubSupplies(0, Map.of());
        supplies.chestSteps = withdrawSteps(CatalogLimits.COUNT_MAX + 1);
        AcquireScript script = new AcquireScript(
                ITEM,
                1,
                alwaysGatherable(),
                supplies,
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(
                        () -> {
                            throw new AssertionError("Реестр не должен запрашиваться: подплан отклоняется по бюджету");
                        },
                        NO_CONDITIONS));
        Optional<TaskNode> plan;
        String reason = null;
        try {
            plan = script.buildSubplan();
        } catch (RuntimeException error) {
            plan = Optional.empty();
            reason = error.getMessage();
        }
        assertTrue(
                plan.isEmpty(),
                "Подплан сверх 256 узлов обязан отклониться, а не запускаться Иначе тест subplanExceedsBudget_NodesOverBudget_BuildFailsWithNumbers перестанет ловить эту поломку. При смене subplanExceedsBudget_NodesOverBudget_BuildFailsWithNumbers проверь это утверждение.");
        assertTrue(
                reason != null && reason.contains("256"),
                "Отклонение по бюджету обязано нести текст с пределом узлов: по нему видно, что упёрлись в бюджет Иначе тест subplanExceedsBudget_NodesOverBudget_BuildFailsWithNumbers перестанет ловить эту поломку. При смене subplanExceedsBudget_NodesOverBudget_BuildFailsWithNumbers проверь это утверждение.");
        assertEquals(
                ScriptStatus.FAILED,
                script.tick(),
                "Провал сборки по бюджету — acquire завершается FAILED, а не крутится безлимитно Иначе тест subplanExceedsBudget_NodesOverBudget_BuildFailsWithNumbers перестанет ловить эту поломку. При смене subplanExceedsBudget_NodesOverBudget_BuildFailsWithNumbers проверь это утверждение.");
        assertTrue(
                script.failureReason() != null && script.failureReason().contains("попыток"),
                "FAILED по бюджету обязан нести причину с числом попыток Иначе тест subplanExceedsBudget_NodesOverBudget_BuildFailsWithNumbers перестанет ловить эту поломку. При смене subplanExceedsBudget_NodesOverBudget_BuildFailsWithNumbers проверь это утверждение.");
    }

    @Test
    void nestedRunnerRunning_WhileWorking_ProgressAndExemptionsDelegate() {
        AtomicInteger ticks = new AtomicInteger();
        AcquireScript script = new AcquireScript(
                ITEM,
                1,
                alwaysGatherable(),
                new StubSupplies(0, Map.of()),
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(
                        () -> ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(3, ticks))),
                        NO_CONDITIONS));
        assertEquals(
                ScriptStatus.RUNNING,
                script.tick(),
                "Первый тик строит подплан и двигает первый шаг Иначе тест nestedRunnerRunning_WhileWorking_ProgressAndExemptionsDelegate перестанет ловить эту поломку. При смене nestedRunnerRunning_WhileWorking_ProgressAndExemptionsDelegate проверь это утверждение.");
        assertEquals(
                ScriptStatus.RUNNING,
                script.tick(),
                "Счётный скрипт ещё работает Иначе тест nestedRunnerRunning_WhileWorking_ProgressAndExemptionsDelegate перестанет ловить эту поломку. При смене nestedRunnerRunning_WhileWorking_ProgressAndExemptionsDelegate проверь это утверждение.");
        assertTrue(
                script.progress().isPresent(),
                "Вложенный шаг счётный — прогресс обязан дойти до acquire Иначе тест nestedRunnerRunning_WhileWorking_ProgressAndExemptionsDelegate перестанет ловить эту поломку. При смене nestedRunnerRunning_WhileWorking_ProgressAndExemptionsDelegate проверь это утверждение.");
        assertEquals(
                2,
                script.progress().get().done(),
                "Прогресс делегируется вложенному раннеру Иначе тест nestedRunnerRunning_WhileWorking_ProgressAndExemptionsDelegate перестанет ловить эту поломку. При смене nestedRunnerRunning_WhileWorking_ProgressAndExemptionsDelegate проверь это утверждение.");
        assertEquals(
                3,
                script.progress().get().target(),
                "Цель прогресса — из вложенного скрипта Иначе тест nestedRunnerRunning_WhileWorking_ProgressAndExemptionsDelegate перестанет ловить эту поломку. При смене nestedRunnerRunning_WhileWorking_ProgressAndExemptionsDelegate проверь это утверждение.");
        assertEquals(
                ScriptStatus.SUCCEEDED,
                script.tick(),
                "Третий тик доводит счётный скрипт до конца Иначе тест nestedRunnerRunning_WhileWorking_ProgressAndExemptionsDelegate перестанет ловить эту поломку. При смене nestedRunnerRunning_WhileWorking_ProgressAndExemptionsDelegate проверь это утверждение.");
    }

    @Test
    void nestedStarted_ControlSignals_ReachLiveStep() {
        List<String> calls = new ArrayList<>();
        AcquireScript script = new AcquireScript(
                ITEM,
                1,
                alwaysGatherable(),
                new StubSupplies(0, Map.of()),
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(
                        () -> ScriptRegistry.of(Map.of("gather", params -> new RecordingInstance(calls))),
                        NO_CONDITIONS));
        script.tick();
        script.suspend();
        script.resume();
        script.cancel();
        assertEquals(
                List.of("suspend", "resume", "cancel"),
                calls,
                "Управление вложенным раннером доходит до живого скрипта шага: иначе отмена оставит духа в мире При смене nestedStarted_ControlSignals_ReachLiveStep проверь это утверждение.");
    }

    @Test
    void control_BeforeFirstTick_ScriptStillRunsAfter() {
        AcquireScript script = script(1, new StubSupplies(0, Map.of()), alwaysGatherable(), new AtomicInteger());
        script.suspend();
        script.resume();
        assertEquals(
                ScriptStatus.RUNNING,
                run(script, 5),
                "Управление до первого тика безопасно: подплан строится после, скрипт не падает Иначе тест control_BeforeFirstTick_ScriptStillRunsAfter перестанет ловить эту поломку. При смене control_BeforeFirstTick_ScriptStillRunsAfter проверь это утверждение.");
    }

    @Test
    void cancel_BeforeFirstTick_ImmediatelyCancelled() {
        AcquireScript script = script(1, new StubSupplies(0, Map.of()), alwaysGatherable(), new AtomicInteger());
        script.cancel();
        assertEquals(
                ScriptStatus.CANCELLED,
                run(script, 5),
                "Отмена до первого тика сразу завершает скрипт: финальный статус CANCELLED, не FAILED Иначе тест cancel_BeforeFirstTick_ImmediatelyCancelled перестанет ловить эту поломку. При смене cancel_BeforeFirstTick_ImmediatelyCancelled проверь это утверждение.");
    }

    @Test
    void suspend_BeforeFirstTick_NewRunnerIsSuspended() {
        AtomicInteger ticks = new AtomicInteger();
        AcquireScript script = new AcquireScript(
                ITEM,
                1,
                alwaysGatherable(),
                new StubSupplies(0, Map.of()),
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(
                        () -> ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(5, ticks))),
                        NO_CONDITIONS));
        script.suspend();
        assertEquals(
                ScriptStatus.RUNNING,
                script.tick(),
                "suspend до первого тика не ломает сборку подплана При смене suspend_BeforeFirstTick_NewRunnerIsSuspended проверь это утверждение.");
        assertEquals(
                ScriptStatus.RUNNING,
                script.tick(),
                "Повторный тик при suspend не двигает вложенный шаг: раннер заморожен Иначе тест suspend_BeforeFirstTick_NewRunnerIsSuspended перестанет ловить эту поломку. При смене suspend_BeforeFirstTick_NewRunnerIsSuspended проверь это утверждение.");
        assertEquals(
                0,
                ticks.get(),
                "suspend до первого тика обязан дойти до нового раннера: шаг не тикает Иначе тест suspend_BeforeFirstTick_NewRunnerIsSuspended перестанет ловить эту поломку. При смене suspend_BeforeFirstTick_NewRunnerIsSuspended проверь это утверждение.");
        script.resume();
        assertEquals(
                ScriptStatus.RUNNING,
                script.tick(),
                "После resume вложенный шаг снова работает Иначе тест suspend_BeforeFirstTick_NewRunnerIsSuspended перестанет ловить эту поломку. При смене suspend_BeforeFirstTick_NewRunnerIsSuspended проверь это утверждение.");
        assertEquals(
                1,
                ticks.get(),
                "resume размораживает раннер, созданный после suspend Иначе тест suspend_BeforeFirstTick_NewRunnerIsSuspended перестанет ловить эту поломку. При смене suspend_BeforeFirstTick_NewRunnerIsSuspended проверь это утверждение.");
    }
}
