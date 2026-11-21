package com.theminespirit.core.engine;

import static com.theminespirit.core.engine.TaskRunnerTestsSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TaskRunnerPart4Tests {
    @Test
    void tick_ScriptInstanceThrows_CancelsLiveInstanceExactlyOnce() {
        AtomicInteger cancels = new AtomicInteger();
        ScriptFactory factory = params -> new ScriptInstance() {
            @Override
            public ScriptStatus tick() {
                throw new IllegalStateException("сломанный тик");
            }

            @Override
            public void cancel() {
                cancels.incrementAndGet();
            }
        };
        TaskRunner runner = TaskRunner.start(
                script(SCRIPT), ScriptRegistry.of(Map.of(SCRIPT, factory)), TestScripts.condition(CHECK, true));
        assertEquals(
                RunStatus.FAILED,
                runner.tick(),
                "исключение из tick обязано провалить узел и не вылететь из серверного тика Иначе тест tick_ScriptInstanceThrows_CancelsLiveInstanceExactlyOnce перестанет ловить эту поломку. При смене tick_ScriptInstanceThrows_CancelsLiveInstanceExactlyOnce проверь это утверждение.");
        assertEquals(
                1,
                cancels.get(),
                "упавший при tick скрипт обязан получить cancel ровно один раз: иначе он не освободит ресурс, "
                        + "а дух продолжит движение по уже сломанной задаче При смене tick_ScriptInstanceThrows_CancelsLiveInstanceExactlyOnce проверь это утверждение.");
    }

    @Test
    void tick_ScriptTickAndCancelThrow_SuppressesCancelError() {
        IllegalStateException cancelError = new IllegalStateException("сбой cancel");
        ScriptFactory factory = params -> new ScriptInstance() {
            @Override
            public ScriptStatus tick() {
                throw new IllegalStateException("сломанный тик");
            }

            @Override
            public void cancel() {
                throw cancelError;
            }
        };
        TaskRunner runner = TaskRunner.start(
                script(SCRIPT), ScriptRegistry.of(Map.of(SCRIPT, factory)), TestScripts.condition(CHECK, true));
        assertEquals(
                RunStatus.FAILED,
                runner.tick(),
                "первичное исключение из tick проваливает узел Иначе тест tick_ScriptTickAndCancelThrow_SuppressesCancelError перестанет ловить эту поломку. При смене tick_ScriptTickAndCancelThrow_SuppressesCancelError проверь это утверждение.");
        Throwable cause = runner.failureCause().orElseThrow();
        assertEquals(
                1,
                cause.getSuppressed().length,
                "исключение из cancel при аварийной отмене не должно подменять исходное: оно идёт в suppressed Иначе тест tick_ScriptTickAndCancelThrow_SuppressesCancelError перестанет ловить эту поломку. При смене tick_ScriptTickAndCancelThrow_SuppressesCancelError проверь это утверждение.");
        assertEquals(
                cancelError,
                cause.getSuppressed()[0],
                "suppressed обязан нести то самое исключение из cancel Иначе тест tick_ScriptTickAndCancelThrow_SuppressesCancelError перестанет ловить эту поломку. При смене tick_ScriptTickAndCancelThrow_SuppressesCancelError проверь это утверждение.");
    }

    @Test
    void tick_ConditionEvaluatorThrows_ReturnsFailureNamingCheckAndException() {
        ConditionRegistry conditions = ConditionRegistry.of(Map.of("boom", params -> {
            throw new IllegalStateException("условие сломалось");
        }));
        TaskNode plan = new ConditionNode("boom", Map.of(), script(SCRIPT), null);
        TaskRunner runner =
                TaskRunner.start(plan, TestScripts.singleRegistry(SCRIPT, TestScripts.succeeding(1)), conditions);
        RunStatus status = runner.tick();
        assertEquals(
                RunStatus.FAILED,
                status,
                "исключение из проверки условия обязано проваливать узел, а не вылетать из тика сущности: "
                        + "иначе сервер упадёт с ошибкой 'Ticking entity' При смене tick_ConditionEvaluatorThrows_ReturnsFailureNamingCheckAndException проверь это утверждение.");
        String reason = runner.failureReason().orElse("");
        assertTrue(
                reason.contains("boom"),
                "причина обязана называть проверку 'boom', из которой прилетело исключение. Фактическое:  Иначе тест tick_ConditionEvaluatorThrows_ReturnsFailureNamingCheckAndException перестанет ловить эту поломку. При смене tick_ConditionEvaluatorThrows_ReturnsFailureNamingCheckAndException проверь это утверждение."
                        + reason);
        assertTrue(
                reason.contains("условие сломалось"),
                "причина обязана нести текст исключения 'условие сломалось'. Фактическое:  Иначе тест tick_ConditionEvaluatorThrows_ReturnsFailureNamingCheckAndException перестанет ловить эту поломку. При смене tick_ConditionEvaluatorThrows_ReturnsFailureNamingCheckAndException проверь это утверждение."
                        + reason);
    }

    @Test
    void tick_ScriptThrowsWithoutMessage_ReportsExceptionTypeAndCause() {
        IllegalStateException failure = new IllegalStateException();
        ScriptFactory factory = params -> new ScriptInstance() {
            @Override
            public ScriptStatus tick() {
                throw failure;
            }
        };
        TaskRunner runner = TaskRunner.start(
                script(SCRIPT), ScriptRegistry.of(Map.of(SCRIPT, factory)), TestScripts.condition(CHECK, true));
        assertEquals(
                RunStatus.FAILED,
                runner.tick(),
                "исключение без сообщения всё равно проваливает узел Иначе тест tick_ScriptThrowsWithoutMessage_ReportsExceptionTypeAndCause перестанет ловить эту поломку. При смене tick_ScriptThrowsWithoutMessage_ReportsExceptionTypeAndCause проверь это утверждение.");
        String reason = runner.failureReason().orElse("");
        assertTrue(
                reason.contains("IllegalStateException"),
                "причина обязана называть класс исключения, даже если сообщения нет: иначе в отчёте 'упал: null'. "
                        + "Фактическое:  При смене tick_ScriptThrowsWithoutMessage_ReportsExceptionTypeAndCause проверь это утверждение."
                        + reason);
        assertSame(
                failure,
                runner.failureCause().orElse(null),
                "failureCause обязан вернуть исходное исключение: по нему в логе виден стек Иначе тест tick_ScriptThrowsWithoutMessage_ReportsExceptionTypeAndCause перестанет ловить эту поломку. При смене tick_ScriptThrowsWithoutMessage_ReportsExceptionTypeAndCause проверь это утверждение.");
    }

    @Test
    void tick_ConditionThrowsWithoutMessage_ReportsExceptionTypeAndCause() {
        IllegalStateException failure = new IllegalStateException();
        ConditionRegistry conditions = ConditionRegistry.of(Map.of("boom", params -> {
            throw failure;
        }));
        TaskNode plan = new ConditionNode("boom", Map.of(), script(SCRIPT), null);
        TaskRunner runner =
                TaskRunner.start(plan, TestScripts.singleRegistry(SCRIPT, TestScripts.succeeding(1)), conditions);
        assertEquals(
                RunStatus.FAILED,
                runner.tick(),
                "исключение без сообщения в условии проваливает узел Иначе тест tick_ConditionThrowsWithoutMessage_ReportsExceptionTypeAndCause перестанет ловить эту поломку. При смене tick_ConditionThrowsWithoutMessage_ReportsExceptionTypeAndCause проверь это утверждение.");
        String reason = runner.failureReason().orElse("");
        assertTrue(
                reason.contains("IllegalStateException"),
                "причина обязана называть класс исключения условия, даже если сообщения нет. Фактическое:  Иначе тест tick_ConditionThrowsWithoutMessage_ReportsExceptionTypeAndCause перестанет ловить эту поломку. При смене tick_ConditionThrowsWithoutMessage_ReportsExceptionTypeAndCause проверь это утверждение."
                        + reason);
        assertSame(
                failure,
                runner.failureCause().orElse(null),
                "failureCause обязан вернуть исходное исключение условия: по нему в логе виден стек Иначе тест tick_ConditionThrowsWithoutMessage_ReportsExceptionTypeAndCause перестанет ловить эту поломку. При смене tick_ConditionThrowsWithoutMessage_ReportsExceptionTypeAndCause проверь это утверждение.");
    }

    @Test
    void cancel_RunningScript_CallsScriptCancelExactlyOnce() {
        AtomicInteger cancels = new AtomicInteger();
        ScriptFactory factory = params -> new ScriptInstance() {
            @Override
            public ScriptStatus tick() {
                return ScriptStatus.RUNNING;
            }

            @Override
            public void cancel() {
                cancels.incrementAndGet();
            }
        };
        TaskRunner runner = TaskRunner.start(
                script(SCRIPT), ScriptRegistry.of(Map.of(SCRIPT, factory)), TestScripts.condition(CHECK, true));
        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "перед отменой скрипт обязан быть в работе Иначе тест cancel_RunningScript_CallsScriptCancelExactlyOnce перестанет ловить эту поломку. При смене cancel_RunningScript_CallsScriptCancelExactlyOnce проверь это утверждение.");
        runner.cancel();
        runner.cancel();
        assertEquals(
                1,
                cancels.get(),
                "отмена задачи обязана донести cancel до живого скрипта ровно один раз: без этого скрипт не освободит "
                        + "занятый блок, предмет в руке или резерв на складе При смене cancel_RunningScript_CallsScriptCancelExactlyOnce проверь это утверждение.");
    }
}
