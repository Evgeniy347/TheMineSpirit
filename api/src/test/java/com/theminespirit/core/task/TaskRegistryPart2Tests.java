package com.theminespirit.core.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.engine.RunProgress;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskRegistryPart2Tests {
    private static final String BOT = "11111111-1111-1111-1111-111111111111";

    @Test
    void watchableRecords_ReservedButNotStarted_HiddenFromWatcherUntilMarkStarted() {
        TaskRegistry registry = new TaskRegistry();
        String reserved = registry.reserve(BOT, null);

        List<TaskRecord> beforeStart = registry.watchableRecords();
        registry.markStarted(reserved);
        List<TaskRecord> afterStart = registry.watchableRecords();

        assertEquals(
                List.of(),
                beforeStart,
                "Бронь, чей старт у платформы ещё не удался, не должна попадать к TaskWatcher: иначе он привяжет её "
                        + "к чужой задаче духа раньше, чем откат убрал запись При смене watchableRecords_ReservedButNotStarted_HiddenFromWatcherUntilMarkStarted проверь это утверждение.");
        assertEquals(
                List.of(reserved),
                afterStart.stream().map(TaskRecord::taskId).toList(),
                "После успешного старта запись обязана опрашиваться watcher Иначе тест watchableRecords_ReservedButNotStarted_HiddenFromWatcherUntilMarkStarted перестанет ловить эту поломку. При смене watchableRecords_ReservedButNotStarted_HiddenFromWatcherUntilMarkStarted проверь это утверждение.");
    }

    @Test
    void closeAll_ActiveTasks_ClosesEveryLogSoWaitingStreamsEndAtOnce() throws Exception {
        TaskRegistry registry = new TaskRegistry();
        String first = registry.reserve(BOT, null);
        String second = registry.reserve("bot-2", null);
        registry.observe(first, TaskState.RUNNING, null, null);

        registry.closeAll();

        assertEquals(
                TaskEventLog.Kind.CLOSED,
                registry.log(first)
                        .awaitAfter(registry.log(first).lastId(), Duration.ofSeconds(10))
                        .kind(),
                "Остановка сервера обязана закрыть журнал идущей задачи: иначе открытый поток событий ждёт "
                        + "кипалайва и держит остановку HTTP-сервера При смене closeAll_ActiveTasks_ClosesEveryLogSoWaitingStreamsEndAtOnce проверь это утверждение.");
        assertEquals(
                TaskEventLog.Kind.CLOSED,
                registry.log(second).awaitAfter(0, Duration.ofSeconds(10)).kind(),
                "Журнал принятой, но не начавшейся задачи тоже обязан закрыться Иначе тест closeAll_ActiveTasks_ClosesEveryLogSoWaitingStreamsEndAtOnce перестанет ловить эту поломку. При смене closeAll_ActiveTasks_ClosesEveryLogSoWaitingStreamsEndAtOnce проверь это утверждение.");
    }

    @Test
    void discard_StartedTask_KeepsRecord() {
        TaskRegistry registry = new TaskRegistry();
        String started = registry.reserve(BOT, null);
        registry.markStarted(started);

        boolean discarded = registry.discard(started);

        assertFalse(
                discarded,
                "Запущенную у платформы задачу откатывать нельзя: дух уже выполняет её план Иначе тест discard_StartedTask_KeepsRecord перестанет ловить эту поломку. При смене discard_StartedTask_KeepsRecord проверь это утверждение.");
        assertEquals(
                1,
                registry.size(),
                "Запись запущенной задачи обязана остаться в реестре Иначе тест discard_StartedTask_KeepsRecord перестанет ловить эту поломку. При смене discard_StartedTask_KeepsRecord проверь это утверждение.");
    }

    @Test
    void watchableRecords_StartedTerminal_Excluded() {
        TaskRegistry registry = new TaskRegistry();
        String finished = registry.reserve(BOT, null);
        registry.markStarted(finished);
        registry.observe(finished, TaskState.SUCCEEDED, null, null);

        assertEquals(
                List.of(),
                registry.watchableRecords(),
                "Завершённая задача не опрашивается Иначе тест watchableRecords_StartedTerminal_Excluded перестанет ловить эту поломку. При смене watchableRecords_StartedTerminal_Excluded проверь это утверждение.");
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

        assertEquals(
                TaskRegistry.MAX_COMPLETED,
                registry.completedCount(),
                "Завершённых в реестре обязано быть не больше  Иначе тест eviction_MoreThanCapCompleted_DropsOldestCompletedKeepsActive перестанет ловить эту поломку. При смене eviction_MoreThanCapCompleted_DropsOldestCompletedKeepsActive проверь это утверждение."
                        + TaskRegistry.MAX_COMPLETED);
        assertTrue(
                registry.get("t-1").isEmpty(),
                "Самая старая завершённая задача вытесняется первой Иначе тест eviction_MoreThanCapCompleted_DropsOldestCompletedKeepsActive перестанет ловить эту поломку. При смене eviction_MoreThanCapCompleted_DropsOldestCompletedKeepsActive проверь это утверждение.");
        assertEquals(
                TaskState.RUNNING,
                registry.get(active).orElseThrow().state(),
                "Активная задача не должна вытесняться ни при каком порядке завершений Иначе тест eviction_MoreThanCapCompleted_DropsOldestCompletedKeepsActive перестанет ловить эту поломку. При смене eviction_MoreThanCapCompleted_DropsOldestCompletedKeepsActive проверь это утверждение.");
    }

    @Test
    void taskJson_RunningTask_MatchesContractJson() {
        TaskRegistry registry = new TaskRegistry();
        String taskId = registry.reserve(BOT, "срубить деревья вокруг");
        registry.observe(taskId, TaskState.RUNNING, new RunProgress(2, 2, "chop_tree", 3, 5), null);

        String json = TaskJson.toJson(registry.get(taskId).orElseThrow());

        assertEquals(
                "{\"task_id\":\"t-1\",\"bot\":\"" + BOT + "\",\"state\":\"running\","
                        + "\"goal\":\"срубить деревья вокруг\","
                        + "\"progress\":{\"step\":2,\"total_steps\":2,\"current_script\":\"chop_tree\","
                        + "\"done\":3,\"target\":5},\"result\":null,\"error\":null}",
                json,
                "JSON задачи — контракт docs/api/README.md: порядок и состав полей фиксированы Иначе тест taskJson_RunningTask_MatchesContractJson перестанет ловить эту поломку. При смене taskJson_RunningTask_MatchesContractJson проверь это утверждение.");
    }

    @Test
    void taskJson_NoProgressAndGoalNull_SerializesNulls() {
        TaskRegistry registry = new TaskRegistry();
        String taskId = registry.reserve(BOT, null);

        String json = TaskJson.toJson(registry.get(taskId).orElseThrow());

        assertEquals(
                "{\"task_id\":\"t-1\",\"bot\":\"" + BOT + "\",\"state\":\"accepted\",\"goal\":null,"
                        + "\"progress\":null,\"result\":null,\"error\":null}",
                json,
                "Отсутствующие goal и progress обязаны сериализоваться как null, а не отбрасываться Иначе тест taskJson_NoProgressAndGoalNull_SerializesNulls перестанет ловить эту поломку. При смене taskJson_NoProgressAndGoalNull_SerializesNulls проверь это утверждение.");
    }

    @Test
    void observe_SameStateWithNewProgress_IsAllowedProgressOnlyUpdate() {
        TaskRegistry registry = new TaskRegistry();
        String taskId = registry.reserve(BOT, null);
        registry.observe(taskId, TaskState.RUNNING, new RunProgress(1, 2, "a", null, null), null);

        registry.observe(taskId, TaskState.RUNNING, new RunProgress(2, 2, "b", null, null), null);

        TaskRecord record = registry.get(taskId).orElseThrow();
        assertEquals(
                2,
                record.progress().step(),
                "Обновление прогресса без смены состояния обязано применяться Иначе тест observe_SameStateWithNewProgress_IsAllowedProgressOnlyUpdate перестанет ловить эту поломку. При смене observe_SameStateWithNewProgress_IsAllowedProgressOnlyUpdate проверь это утверждение.");
        assertEquals(
                "b",
                record.progress().currentScript(),
                "Текущий скрипт обязан обновляться прогрессом Иначе тест observe_SameStateWithNewProgress_IsAllowedProgressOnlyUpdate перестанет ловить эту поломку. При смене observe_SameStateWithNewProgress_IsAllowedProgressOnlyUpdate проверь это утверждение.");
    }
}
