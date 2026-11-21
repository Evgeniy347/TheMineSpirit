package com.theminespirit.core.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.engine.RunProgress;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class TaskRegistryTests {
    private static final String BOT = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_BOT = "22222222-2222-2222-2222-222222222222";

    @Test
    void reserve_NewBot_CreatesAcceptedTaskWithGrowingId() {
        TaskRegistry registry = new TaskRegistry();

        String first = registry.reserve(BOT, "срубить деревья");
        String second = registry.reserve(OTHER_BOT, null);

        assertEquals(
                "t-1",
                first,
                "Первая задача реестра обязана получить id t-1: монотонная нумерация — контракт клиента Иначе тест reserve_NewBot_CreatesAcceptedTaskWithGrowingId перестанет ловить эту поломку. При смене reserve_NewBot_CreatesAcceptedTaskWithGrowingId проверь это утверждение.");
        assertEquals(
                "t-2",
                second,
                "Вторая задача обязана получить t-2: id растут и не повторяются Иначе тест reserve_NewBot_CreatesAcceptedTaskWithGrowingId перестанет ловить эту поломку. При смене reserve_NewBot_CreatesAcceptedTaskWithGrowingId проверь это утверждение.");
        TaskRecord record = registry.get(first).orElseThrow();
        assertEquals(
                TaskState.ACCEPTED,
                record.state(),
                "Новая задача стартует в accepted до первого тика духа Иначе тест reserve_NewBot_CreatesAcceptedTaskWithGrowingId перестанет ловить эту поломку. При смене reserve_NewBot_CreatesAcceptedTaskWithGrowingId проверь это утверждение.");
        assertEquals(
                "срубить деревья",
                record.goal(),
                "Цель обязана сохраняться в записи задачи Иначе тест reserve_NewBot_CreatesAcceptedTaskWithGrowingId перестанет ловить эту поломку. При смене reserve_NewBot_CreatesAcceptedTaskWithGrowingId проверь это утверждение.");
        assertNull(
                record.progress(),
                "Прогресса у свежей задачи ещё нет Иначе тест reserve_NewBot_CreatesAcceptedTaskWithGrowingId перестанет ловить эту поломку. При смене reserve_NewBot_CreatesAcceptedTaskWithGrowingId проверь это утверждение.");
    }

    @Test
    void reserve_BotWithActiveTask_ThrowsBusyWithActiveTaskId() {
        TaskRegistry registry = new TaskRegistry();
        String active = registry.reserve(BOT, "идти домой");

        TaskRegistry.BusyException busy = assertThrows(
                TaskRegistry.BusyException.class,
                () -> registry.reserve(BOT, "вторая цель"),
                "Повторный reserve занятого духа обязан падать BusyException: очередь выключена, а 409 клиенту "
                        + "нужен id активной задачи Иначе тест reserve_BotWithActiveTask_ThrowsBusyWithActiveTaskId перестанет ловить эту поломку. При смене reserve_BotWithActiveTask_ThrowsBusyWithActiveTaskId проверь это утверждение.");

        assertEquals(
                active,
                busy.activeTaskId(),
                "BusyException обязан называть активную задачу для ответа 409 Иначе тест reserve_BotWithActiveTask_ThrowsBusyWithActiveTaskId перестанет ловить эту поломку. При смене reserve_BotWithActiveTask_ThrowsBusyWithActiveTaskId проверь это утверждение.");
        assertEquals(
                1,
                registry.size(),
                "Отказ не должен оставлять полузабронированную запись в реестре Иначе тест reserve_BotWithActiveTask_ThrowsBusyWithActiveTaskId перестанет ловить эту поломку. При смене reserve_BotWithActiveTask_ThrowsBusyWithActiveTaskId проверь это утверждение.");
    }

    @Test
    void reserve_BotFreedAfterTerminal_OneActiveTaskRuleHolds() {
        TaskRegistry registry = new TaskRegistry();
        String first = registry.reserve(BOT, null);
        registry.observe(first, TaskState.RUNNING, new RunProgress(1, 1, "goto", null, null), null);
        registry.observe(first, TaskState.SUCCEEDED, null, null);

        String second = registry.reserve(BOT, "новая цель");

        assertEquals(
                "t-2",
                second,
                "После завершения первой задачи дух обязан снова принимать новые Иначе тест reserve_BotFreedAfterTerminal_OneActiveTaskRuleHolds перестанет ловить эту поломку. При смене reserve_BotFreedAfterTerminal_OneActiveTaskRuleHolds проверь это утверждение.");
    }

    @ParameterizedTest(name = "{0} → {1}: {2}")
    @CsvSource({
        "ACCEPTED, RUNNING, true",
        "ACCEPTED, SUCCEEDED, true",
        "ACCEPTED, FAILED, true",
        "ACCEPTED, CANCELLED, true",
        "ACCEPTED, ACCEPTED, true",
        "RUNNING, SUCCEEDED, true",
        "RUNNING, FAILED, true",
        "RUNNING, CANCELLED, true",
        "RUNNING, RUNNING, true",
        "RUNNING, ACCEPTED, false",
        "SUCCEEDED, RUNNING, false",
        "SUCCEEDED, FAILED, false",
        "SUCCEEDED, CANCELLED, false",
        "SUCCEEDED, ACCEPTED, false",
        "FAILED, RUNNING, false",
        "FAILED, SUCCEEDED, false",
        "FAILED, CANCELLED, false",
        "FAILED, ACCEPTED, false",
        "CANCELLED, RUNNING, false",
        "CANCELLED, SUCCEEDED, false",
        "CANCELLED, FAILED, false",
        "CANCELLED, ACCEPTED, false"
    })
    void canTransition_TableCase_MatchesLifecycleTable(TaskState from, TaskState to, boolean allowed) {
        assertEquals(
                allowed,
                from.canTransitionTo(to),
                "Таблица переходов задачи: " + from.wire() + " → " + to.wire() + " должна быть "
                        + (allowed
                                ? "разрешена"
                                : "запрещена Иначе тест canTransition_TableCase_MatchesLifecycleTable перестанет ловить эту поломку. При смене canTransition_TableCase_MatchesLifecycleTable проверь это утверждение."));
    }

    @ParameterizedTest
    @EnumSource(
            value = TaskState.class,
            names = {"SUCCEEDED", "FAILED", "CANCELLED"})
    void observe_TerminalState_IllegalTransitionThrowsAndKeepsRecord(TaskState terminal) {
        TaskRegistry registry = new TaskRegistry();
        String taskId = registry.reserve(BOT, null);
        registry.observe(taskId, terminal, null, null);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> registry.observe(taskId, TaskState.RUNNING, null, null),
                "Переход из терминального состояния обязан отвергаться: иначе клиент увидит «ожившую» задачу При смене observe_TerminalState_IllegalTransitionThrowsAndKeepsRecord проверь это утверждение.");

        assertTrue(
                failure.getMessage().contains(taskId),
                "Сообщение о недопустимом переходе обязано называть task_id:  Иначе тест observe_TerminalState_IllegalTransitionThrowsAndKeepsRecord перестанет ловить эту поломку. При смене observe_TerminalState_IllegalTransitionThrowsAndKeepsRecord проверь это утверждение."
                        + failure.getMessage());
        assertEquals(
                terminal,
                registry.get(taskId).orElseThrow().state(),
                "После отказа запись обязана сохранить терминальное состояние Иначе тест observe_TerminalState_IllegalTransitionThrowsAndKeepsRecord перестанет ловить эту поломку. При смене observe_TerminalState_IllegalTransitionThrowsAndKeepsRecord проверь это утверждение.");
    }

    @Test
    void observe_StateAndProgress_UpdatesRecordAndAppendsEvents() {
        TaskRegistry registry = new TaskRegistry();
        String taskId = registry.reserve(BOT, "цель");
        RunProgress progress = new RunProgress(2, 3, "chop_tree", 3, 5);

        long before = registry.log(taskId).lastId();
        List<TaskEvent> events = registry.observe(taskId, TaskState.RUNNING, progress, null);

        assertEquals(
                2,
                events.size(),
                "ACCEPTED → RUNNING с прогрессом даёт progress и state — два кадра Иначе тест observe_StateAndProgress_UpdatesRecordAndAppendsEvents перестанет ловить эту поломку. При смене observe_StateAndProgress_UpdatesRecordAndAppendsEvents проверь это утверждение.");
        assertEquals(
                TaskEvent.TYPE_PROGRESS,
                events.get(0).type(),
                "Прогресс идёт перед сменой состояния Иначе тест observe_StateAndProgress_UpdatesRecordAndAppendsEvents перестанет ловить эту поломку. При смене observe_StateAndProgress_UpdatesRecordAndAppendsEvents проверь это утверждение.");
        assertEquals(
                TaskEvent.TYPE_STATE,
                events.get(1).type(),
                "Смена состояния — вторым кадром Иначе тест observe_StateAndProgress_UpdatesRecordAndAppendsEvents перестанет ловить эту поломку. При смене observe_StateAndProgress_UpdatesRecordAndAppendsEvents проверь это утверждение.");
        TaskRecord record = registry.get(taskId).orElseThrow();
        assertEquals(
                TaskState.RUNNING,
                record.state(),
                "Запись обязана перейти в running Иначе тест observe_StateAndProgress_UpdatesRecordAndAppendsEvents перестанет ловить эту поломку. При смене observe_StateAndProgress_UpdatesRecordAndAppendsEvents проверь это утверждение.");
        assertEquals(
                progress,
                record.progress(),
                "Прогресс обязан сохраняться в записи Иначе тест observe_StateAndProgress_UpdatesRecordAndAppendsEvents перестанет ловить эту поломку. При смене observe_StateAndProgress_UpdatesRecordAndAppendsEvents проверь это утверждение.");
        assertEquals(
                before + events.size(),
                registry.log(taskId).lastId(),
                "События observe обязаны попадать в журнал задачи для SSE Иначе тест observe_StateAndProgress_UpdatesRecordAndAppendsEvents перестанет ловить эту поломку. При смене observe_StateAndProgress_UpdatesRecordAndAppendsEvents проверь это утверждение.");
    }

    @Test
    void observe_Failure_AppendsScriptFailedBeforeState() {
        TaskRegistry registry = new TaskRegistry();
        String taskId = registry.reserve(BOT, null);
        registry.observe(taskId, TaskState.RUNNING, new RunProgress(1, 1, "goto", null, null), null);
        TaskEventLog log = registry.log(taskId);
        long before = log.lastId();

        registry.observe(taskId, TaskState.FAILED, null, "точка недостижима");

        List<TaskEvent> appended = log.since(before).events();
        assertEquals(
                2,
                appended.size(),
                "Провал обязан дать script_failed и state — два кадра Иначе тест observe_Failure_AppendsScriptFailedBeforeState перестанет ловить эту поломку. При смене observe_Failure_AppendsScriptFailedBeforeState проверь это утверждение.");
        assertEquals(
                TaskEvent.TYPE_SCRIPT_FAILED,
                appended.get(0).type(),
                "script_failed обязан идти قبل финального state, чтобы клиент увидел причину Иначе тест observe_Failure_AppendsScriptFailedBeforeState перестанет ловить эту поломку. При смене observe_Failure_AppendsScriptFailedBeforeState проверь это утверждение.");
        assertEquals(
                "goto",
                appended.get(0).data().get("script"),
                "script_failed называет упавший скрипт Иначе тест observe_Failure_AppendsScriptFailedBeforeState перестанет ловить эту поломку. При смене observe_Failure_AppendsScriptFailedBeforeState проверь это утверждение.");
        assertEquals(
                "точка недостижима",
                appended.get(0).data().get("reason"),
                "script_failed несёт причину Иначе тест observe_Failure_AppendsScriptFailedBeforeState перестанет ловить эту поломку. При смене observe_Failure_AppendsScriptFailedBeforeState проверь это утверждение.");
        assertEquals(
                TaskEvent.TYPE_STATE,
                appended.get(1).type(),
                "Финальный кадр — state Иначе тест observe_Failure_AppendsScriptFailedBeforeState перестанет ловить эту поломку. При смене observe_Failure_AppendsScriptFailedBeforeState проверь это утверждение.");
    }

    @Test
    void get_UnknownTask_ReturnsEmpty() {
        TaskRegistry registry = new TaskRegistry();

        Optional<TaskRecord> missing = registry.get("t-999");

        assertTrue(
                missing.isEmpty(),
                "Неизвестная задача обязана давать пустой Optional: клиент получит 404 Иначе тест get_UnknownTask_ReturnsEmpty перестанет ловить эту поломку. При смене get_UnknownTask_ReturnsEmpty проверь это утверждение.");
    }

    @Test
    void activeForBot_OnlyActiveRecords_MatchesOneTaskRule() {
        TaskRegistry registry = new TaskRegistry();
        String first = registry.reserve(BOT, null);
        registry.observe(first, TaskState.SUCCEEDED, null, null);

        assertTrue(
                registry.activeForBot(BOT).isEmpty(),
                "Завершённая задача не должна занимать духа Иначе тест activeForBot_OnlyActiveRecords_MatchesOneTaskRule перестанет ловить эту поломку. При смене activeForBot_OnlyActiveRecords_MatchesOneTaskRule проверь это утверждение.");
        String second = registry.reserve(BOT, null);
        assertEquals(
                second,
                registry.activeForBot(BOT).orElseThrow().taskId(),
                "Активная задача обязана находиться по bot Иначе тест activeForBot_OnlyActiveRecords_MatchesOneTaskRule перестанет ловить эту поломку. При смене activeForBot_OnlyActiveRecords_MatchesOneTaskRule проверь это утверждение.");
    }
}
