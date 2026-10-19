package com.theminespirit.core.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.theminespirit.core.engine.RunProgress;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TaskEventDiffTests {
    private static TaskRecord record(TaskState state, RunProgress progress, String error) {
        return new TaskRecord("t-1", "bot", state, "цель", progress, null, error);
    }

    private static TaskRecord recordWithResult(TaskState state, Object result) {
        return new TaskRecord("t-1", "bot", state, "цель", null, result, null);
    }

    private static TaskEvent stateEvent(TaskRecord previous, TaskRecord current) {
        return TaskEventDiff.between(previous, current).stream()
                .filter(event -> TaskEvent.TYPE_STATE.equals(event.type()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("переход обязан дать state-событие"));
    }

    @Test
    void between_SucceededWithResult_StateEventCarriesResult() {
        Object result = Map.of("chopped", 2);

        TaskEvent event =
                stateEvent(record(TaskState.RUNNING, null, null), recordWithResult(TaskState.SUCCEEDED, result));

        assertEquals("succeeded", event.data().get("state"), "state-событие обязано называть succeeded");
        assertEquals(
                result,
                event.data().get("result"),
                "state-событие успеха обязано нести итог: по нему клиент узнаёт, что сделано, без второго запроса");
    }

    @Test
    void between_SucceededWithoutResult_StateEventHasNullResult() {
        TaskEvent event =
                stateEvent(record(TaskState.ACCEPTED, null, null), recordWithResult(TaskState.SUCCEEDED, null));

        assertTrue(
                event.data().containsKey("result"),
                "state-событие успеха обязано содержать ключ result даже при пустом итоге: клиент отличает 'нет "
                        + "ключа' от 'итога нет'");
        assertNull(event.data().get("result"), "успех без итога обязан нести result == null");
    }

    @Test
    void between_Failed_NoResultKeyInScriptFailedOrState() {
        TaskRecord previous = record(TaskState.RUNNING, new RunProgress(1, 2, "goto", null, null), null);
        TaskRecord current = record(TaskState.FAILED, null, "упал");

        List<TaskEvent> events = TaskEventDiff.between(previous, current);

        for (TaskEvent event : events) {
            assertTrue(
                    !event.data().containsKey("result"),
                    "событие провала (" + event.type() + ") не должно нести result: итог принадлежит только успеху");
        }
    }

    static Stream<Arguments> table() {
        RunProgress first = new RunProgress(1, 3, "a", null, null);
        RunProgress second = new RunProgress(2, 3, "b", 1, 4);
        return Stream.of(
                Arguments.of(
                        record(TaskState.ACCEPTED, null, null),
                        record(TaskState.ACCEPTED, null, null),
                        List.of(),
                        "одинаковые кадры не дают событий"),
                Arguments.of(
                        record(TaskState.ACCEPTED, null, null),
                        record(TaskState.RUNNING, null, null),
                        List.of(TaskEvent.TYPE_STATE),
                        "переход в running даёт state"),
                Arguments.of(
                        record(TaskState.RUNNING, first, null),
                        record(TaskState.RUNNING, second, null),
                        List.of(TaskEvent.TYPE_PROGRESS),
                        "смена шага/скрипта/счётчика даёт progress"),
                Arguments.of(
                        record(TaskState.RUNNING, first, null),
                        record(TaskState.RUNNING, first, null),
                        List.of(),
                        "тот же прогресс без смены состояния не даёт событий"),
                Arguments.of(
                        record(TaskState.ACCEPTED, null, null),
                        record(TaskState.RUNNING, first, null),
                        List.of(TaskEvent.TYPE_PROGRESS, TaskEvent.TYPE_STATE),
                        "переход с новым прогрессом даёт progress, затем state"),
                Arguments.of(
                        record(TaskState.RUNNING, first, "упал"),
                        record(TaskState.FAILED, null, "упал"),
                        List.of(TaskEvent.TYPE_SCRIPT_FAILED, TaskEvent.TYPE_STATE),
                        "провал даёт script_failed перед финальным state"),
                Arguments.of(
                        record(TaskState.ACCEPTED, null, null),
                        record(TaskState.CANCELLED, null, null),
                        List.of(TaskEvent.TYPE_STATE),
                        "отмена без прогресса даёт только state"),
                Arguments.of(
                        record(TaskState.RUNNING, second, null),
                        record(TaskState.FAILED, null, "падение"),
                        List.of(TaskEvent.TYPE_SCRIPT_FAILED, TaskEvent.TYPE_STATE),
                        "провал после прогресса даёт script_failed и state без лишнего progress"));
    }

    @ParameterizedTest(name = "{index}: {2}")
    @MethodSource("table")
    void between_TableCase_ProducesExpectedTypes(
            TaskRecord previous, TaskRecord current, List<String> expected, String label) {
        List<TaskEvent> events = TaskEventDiff.between(previous, current);

        assertEquals(
                expected,
                events.stream().map(TaskEvent::type).toList(),
                "Дифф кадров (" + label + ") обязан давать ровно типы " + expected);
    }

    @ParameterizedTest
    @MethodSource("table")
    void between_AnyCase_EventDataMatchesCurrentFrame(
            TaskRecord previous, TaskRecord current, List<String> expected, String label) {
        for (TaskEvent event : TaskEventDiff.between(previous, current)) {
            switch (event.type()) {
                case TaskEvent.TYPE_STATE ->
                    assertEquals(
                            current.state().wire(),
                            event.data().get("state"),
                            "Кадр state обязан нести текущее состояние (" + label + ")");
                case TaskEvent.TYPE_PROGRESS -> {
                    assertEquals(
                            current.progress().step(),
                            event.data().get("step"),
                            "Кадр progress обязан нести текущий шаг (" + label + ")");
                    assertEquals(
                            current.progress().totalSteps(),
                            event.data().get("total_steps"),
                            "Кадр progress обязан нести число шагов (" + label + ")");
                }
                case TaskEvent.TYPE_SCRIPT_FAILED -> {
                    assertEquals(
                            previous.progress() == null
                                    ? null
                                    : previous.progress().currentScript(),
                            event.data().get("script"),
                            "script_failed обязан называть скрипт, работавший до провала (" + label + ")");
                    assertEquals(
                            current.error(),
                            event.data().get("reason"),
                            "script_failed обязан нести причину провала (" + label + ")");
                }
                default -> fail("Неизвестный тип события " + event.type());
            }
        }
    }
}
