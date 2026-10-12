package com.theminespirit.core.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.engine.RunProgress;
import java.util.List;
import java.util.Map;
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

        assertEquals("t-1", first, "Первая задача реестра обязана получить id t-1: монотонная нумерация — контракт клиента");
        assertEquals("t-2", second, "Вторая задача обязана получить t-2: id растут и не повторяются");
        TaskRecord record = registry.get(first).orElseThrow();
        assertEquals(TaskState.ACCEPTED, record.state(), "Новая задача стартует в accepted до первого тика духа");
        assertEquals("срубить деревья", record.goal(), "Цель обязана сохраняться в записи задачи");
        assertNull(record.progress(), "Прогресса у свежей задачи ещё нет");
    }

    @Test
    void reserve_BotWithActiveTask_ThrowsBusyWithActiveTaskId() {
        TaskRegistry registry = new TaskRegistry();
        String active = registry.reserve(BOT, "идти домой");

        TaskRegistry.BusyException busy = assertThrows(TaskRegistry.BusyException.class,
                () -> registry.reserve(BOT, "вторая цель"),
                "Повторный reserve занятого духа обязан падать BusyException: очередь выключена, а 409 клиенту "
                        + "нужен id активной задачи");

        assertEquals(active, busy.activeTaskId(), "BusyException обязан называть активную задачу для ответа 409");
        assertEquals(1, registry.size(), "Отказ не должен оставлять полузабронированную запись в реестре");
    }

    @Test
    void reserve_BotFreedAfterTerminal_OneActiveTaskRuleHolds() {
        TaskRegistry registry = new TaskRegistry();
        String first = registry.reserve(BOT, null);
        registry.observe(first, TaskState.RUNNING, RunProgress.singleStep("goto"), null);
        registry.observe(first, TaskState.SUCCEEDED, null, null);

        String second = registry.reserve(BOT, "новая цель");

        assertEquals("t-2", second, "После завершения первой задачи дух обязан снова принимать новые");
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
        assertEquals(allowed, from.canTransitionTo(to),
                "Таблица переходов задачи: " + from.wire() + " → " + to.wire() + " должна быть "
                        + (allowed ? "разрешена" : "запрещена"));
    }

    @ParameterizedTest
    @EnumSource(value = TaskState.class, names = {"SUCCEEDED", "FAILED", "CANCELLED"})
    void observe_TerminalState_IllegalTransitionThrowsAndKeepsRecord(TaskState terminal) {
        TaskRegistry registry = new TaskRegistry();
        String taskId = registry.reserve(BOT, null);
        registry.observe(taskId, terminal, null, null);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> registry.observe(taskId, TaskState.RUNNING, null, null),
                "Переход из терминального состояния обязан отвергаться: иначе клиент увидит «ожившую» задачу");

        assertTrue(failure.getMessage().contains(taskId),
                "Сообщение о недопустимом переходе обязано называть task_id: " + failure.getMessage());
        assertEquals(terminal, registry.get(taskId).orElseThrow().state(),
                "После отказа запись обязана сохранить терминальное состояние");
    }

    @Test
    void observe_StateAndProgress_UpdatesRecordAndAppendsEvents() {
        TaskRegistry registry = new TaskRegistry();
        String taskId = registry.reserve(BOT, "цель");
        RunProgress progress = new RunProgress(2, 3, "chop_tree", 3, 5);

        List<TaskEvent> events = registry.observe(taskId, TaskState.RUNNING, progress, null);

        assertEquals(2, events.size(), "ACCEPTED → RUNNING с прогрессом даёт progress и state — два кадра");
        assertEquals(TaskEvent.TYPE_PROGRESS, events.get(0).type(), "Прогресс идёт перед сменой состояния");
        assertEquals(TaskEvent.TYPE_STATE, events.get(1).type(), "Смена состояния — вторым кадром");
        TaskRecord record = registry.get(taskId).orElseThrow();
        assertEquals(TaskState.RUNNING, record.state(), "Запись обязана перейти в running");
        assertEquals(progress, record.progress(), "Прогресс обязан сохраняться в записи");
        assertEquals(events.size(), registry.appendedEventCount(taskId),
                "События observe обязаны попадать в журнал задачи для SSE");
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
        assertEquals(2, appended.size(), "Провал обязан дать script_failed и state — два кадра");
        assertEquals(TaskEvent.TYPE_SCRIPT_FAILED, appended.get(0).type(),
                "script_failed обязан идти قبل финального state, чтобы клиент увидел причину");
        assertEquals("goto", appended.get(0).data().get("script"), "script_failed называет упавший скрипт");
        assertEquals("точка недостижима", appended.get(0).data().get("reason"), "script_failed несёт причину");
        assertEquals(TaskEvent.TYPE_STATE, appended.get(1).type(), "Финальный кадр — state");
    }

    @Test
    void get_UnknownTask_ReturnsEmpty() {
        TaskRegistry registry = new TaskRegistry();

        Optional<TaskRecord> missing = registry.get("t-999");

        assertTrue(missing.isEmpty(), "Неизвестная задача обязана давать пустой Optional: клиент получит 404");
    }

    @Test
    void activeForBot_OnlyActiveRecords_MatchesOneTaskRule() {
        TaskRegistry registry = new TaskRegistry();
        String first = registry.reserve(BOT, null);
        registry.observe(first, TaskState.SUCCEEDED, null, null);

        assertTrue(registry.activeForBot(BOT).isEmpty(), "Завершённая задача не должна занимать духа");
        String second = registry.reserve(BOT, null);
        assertEquals(second, registry.activeForBot(BOT).orElseThrow().taskId(),
                "Активная задача обязана находиться по bot");
    }

    @Test
    void activeRecords_TerminalExcluded_WatcherPollsOnlyActive() {
        TaskRegistry registry = new TaskRegistry();
        String done = registry.reserve(BOT, null);
        registry.observe(done, TaskState.SUCCEEDED, null, null);
        String running = registry.reserve(OTHER_BOT, null);
        registry.observe(running, TaskState.RUNNING, null, null);

        List<TaskRecord> active = registry.activeRecords();

        assertEquals(List.of(running), active.stream().map(TaskRecord::taskId).toList(),
                "activeRecords обязан отдавать только нетерминальные записи: TaskWatcher опрашивает только их");
    }

    @Test
    void watchableRecords_ReservedButNotStarted_HiddenFromWatcherUntilMarkStarted() {
        TaskRegistry registry = new TaskRegistry();
        String reserved = registry.reserve(BOT, null);

        List<TaskRecord> beforeStart = registry.watchableRecords();
        registry.markStarted(reserved);
        List<TaskRecord> afterStart = registry.watchableRecords();

        assertEquals(List.of(), beforeStart,
                "Бронь, чей старт у платформы ещё не удался, не должна попадать к TaskWatcher: иначе он привяжет её "
                        + "к чужой задаче духа раньше, чем откат убрал запись");
        assertEquals(List.of(reserved), afterStart.stream().map(TaskRecord::taskId).toList(),
                "После успешного старта запись обязана опрашиваться watcher");
    }

    @Test
    void discard_StartedTask_KeepsRecord() {
        TaskRegistry registry = new TaskRegistry();
        String started = registry.reserve(BOT, null);
        registry.markStarted(started);

        boolean discarded = registry.discard(started);

        assertFalse(discarded, "Запущенную у платформы задачу откатывать нельзя: дух уже выполняет её план");
        assertEquals(1, registry.size(), "Запись запущенной задачи обязана остаться в реестре");
    }

    @Test
    void watchableRecords_StartedTerminal_Excluded() {
        TaskRegistry registry = new TaskRegistry();
        String finished = registry.reserve(BOT, null);
        registry.markStarted(finished);
        registry.observe(finished, TaskState.SUCCEEDED, null, null);

        assertEquals(List.of(), registry.watchableRecords(), "Завершённая задача не опрашивается");
    }

    @Test
    void eviction_MoreThanCapCompleted_DropsOldestCompletedKeepsActive() {
        TaskRegistry registry = new TaskRegistry();
        for (int i = 0; i < TaskRegistry.MAX_COMPLETED + 10; i++) {
            String taskId = registry.reserve("bot-" + i, null);
            registry.observe(taskId, TaskState.SUCCEEDED, null, null);
        }
        String active = registry.reserve(BOT, "долгая");
        registry.observe(active, TaskState.RUNNING, null, null);

        assertEquals(TaskRegistry.MAX_COMPLETED, registry.completedCount(),
                "Завершённых в реестре обязано быть не больше " + TaskRegistry.MAX_COMPLETED);
        assertTrue(registry.get("t-1").isEmpty(), "Самая старая завершённая задача вытесняется первой");
        assertEquals(TaskState.RUNNING, registry.get(active).orElseThrow().state(),
                "Активная задача не должна вытесняться ни при каком порядке завершений");
    }

    @Test
    void taskJson_RunningTask_MatchesContractJson() {
        TaskRegistry registry = new TaskRegistry();
        String taskId = registry.reserve(BOT, "срубить деревья вокруг");
        registry.observe(taskId, TaskState.RUNNING, new RunProgress(2, 2, "chop_tree", 3, 5), null);

        String json = TaskJson.toJson(registry.get(taskId).orElseThrow());

        assertEquals("{\"task_id\":\"t-1\",\"bot\":\"" + BOT + "\",\"state\":\"running\","
                        + "\"goal\":\"срубить деревья вокруг\","
                        + "\"progress\":{\"step\":2,\"total_steps\":2,\"current_script\":\"chop_tree\","
                        + "\"done\":3,\"target\":5},\"result\":null,\"error\":null}",
                json, "JSON задачи — контракт docs/API.md: порядок и состав полей фиксированы");
    }

    @Test
    void taskJson_NoProgressAndGoalNull_SerializesNulls() {
        TaskRegistry registry = new TaskRegistry();
        String taskId = registry.reserve(BOT, null);

        String json = TaskJson.toJson(registry.get(taskId).orElseThrow());

        assertEquals("{\"task_id\":\"t-1\",\"bot\":\"" + BOT + "\",\"state\":\"accepted\",\"goal\":null,"
                        + "\"progress\":null,\"result\":null,\"error\":null}",
                json, "Отсутствующие goal и progress обязаны сериализоваться как null, а не отбрасываться");
    }

    @Test
    void observe_SameStateWithNewProgress_IsAllowedProgressOnlyUpdate() {
        TaskRegistry registry = new TaskRegistry();
        String taskId = registry.reserve(BOT, null);
        registry.observe(taskId, TaskState.RUNNING, new RunProgress(1, 2, "a", null, null), null);

        registry.observe(taskId, TaskState.RUNNING, new RunProgress(2, 2, "b", null, null), null);

        TaskRecord record = registry.get(taskId).orElseThrow();
        assertEquals(2, record.progress().step(), "Обновление прогресса без смены состояния обязано применяться");
        assertEquals("b", record.progress().currentScript(), "Текущий скрипт обязан обновляться прогрессом");
    }
}
