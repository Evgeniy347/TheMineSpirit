package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.api.ConditionCatalog;
import com.theminespirit.core.script.api.ScriptCatalog;
import com.theminespirit.core.task.TaskRegistry;
import com.theminespirit.core.task.TaskState;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

class TaskRoutesTestsPart7 {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BOT = "6f1c2d3e-0000-4000-8000-000000000001";
    private static final ScriptCatalog CATALOG =
            CoreScripts.defaultCatalog().restrictedTo(Set.of("scan", "goto", "follow", "collect_drops", "wait"));
    private static final WorldObservation OBSERVATION = new WorldObservation(
            BOT, 1, new BlockCoord(0, 64, 0), List.of(), List.of(), List.of(), List.of(), ScanRadius.DEFAULT.value());

    private TaskRegistry registry;
    private FakeTaskGateway gateway;
    private PlanningService planning;
    private Idempotency idempotency;
    private SseSlots slots;
    private final FakeBoardGateway board = new FakeBoardGateway();
    private Router router;

    @BeforeEach
    void setUp() {
        registry = new TaskRegistry();
        planning = new PlanningService(CATALOG, ConditionCatalog.empty());
        gateway = new FakeTaskGateway().observing(OBSERVATION).planning(planning);
        idempotency = new IdempotencyCache();
        slots = new SseSlots();
        router = Router.of(new TaskRoutes(registry, gateway, planning, idempotency, slots, board).routes());
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private RestResponse send(String method, String path, String body, Map<String, String> headers) {
        return router.handle(new RestRequest(method, path, Map.of(), headers, utf8(body)));
    }

    private RestResponse send(String method, String path, String body) {
        return send(method, path, body, Map.of());
    }

    private String postTasks(String goalJson) {
        String body = "{\"request_id\":\"" + uuid() + "\"," + goalJson + "}";
        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);
        assertEquals(
                202,
                response.status(),
                "Постановка задачи обязана приниматься: тело " + response.body()
                        + " Иначе тест observing перестанет ловить эту поломку. При смене observing проверь это утверждение.");
        return response.body();
    }

    private String taskId(String acceptedBody) throws Exception {
        return MAPPER.readTree(acceptedBody).get("task_id").asText();
    }

    private String runToState(String taskId, TaskState state, String error) {
        return registry.observe(taskId, state, null, error).toString();
    }

    static Stream<Arguments> table() {
        return Stream.of(
                Arguments.of(
                        "POST",
                        "tasks",
                        "{\"request_id\":\"" + uuid()
                                + "\",\"plan\":{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":1,\"y\":64,\"z\":2}}}",
                        202,
                        "\"state\":\"accepted\""),
                Arguments.of(
                        "POST",
                        "tasks",
                        "{\"request_id\":\"" + uuid()
                                + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}",
                        202,
                        "\"state\":\"accepted\""),
                Arguments.of("POST", "tasks", "{", 400, "не разобрано как JSON"),
                Arguments.of("POST", "tasks", "{\"request_id\":\"" + uuid() + "\"}", 400, "goal или plan"),
                Arguments.of("POST", "tasks", "{\"request_id\":\"не-uuid\",\"goal\":\"цель\"}", 400, "request_id"),
                Arguments.of(
                        "POST",
                        "tasks",
                        "{\"request_id\":\"" + uuid() + "\",\"goal\":\"цель\",\"foo\":1}",
                        400,
                        "'foo'"),
                Arguments.of(
                        "POST",
                        "tasks",
                        "{\"request_id\":\"" + uuid() + "\",\"goal\":\"спой гимн\"}",
                        422,
                        "не распознана"),
                Arguments.of(
                        "POST",
                        "tasks",
                        "{\"request_id\":\"" + uuid() + "\",\"plan\":{\"type\":\"script\",\"script\":\"return_home\"}}",
                        422,
                        "return_home"),
                Arguments.of(
                        "POST",
                        "plan",
                        "{\"plan\":{\"type\":\"script\",\"script\":\"goto\","
                                + "\"params\":{\"x\":1,\"y\":64,\"z\":2}}}",
                        200,
                        "\"valid\":true"),
                Arguments.of(
                        "POST",
                        "plan",
                        "{\"plan\":{\"type\":\"script\",\"script\":\"return_home\"}}",
                        422,
                        "return_home"),
                Arguments.of("POST", "plan", "{\"plan\":{\"type\":\"script\"}}", 422, "script"),
                Arguments.of("POST", "plan", "{}", 400, "нет поля 'plan'"),
                Arguments.of("POST", "plan", "[1]", 400, "JSON-объектом"),
                Arguments.of("POST", "plan", "{", 400, "не разобрано как JSON"));
    }

    @Test
    void delete_UnknownTask_ReturnsNotFound() {
        RestResponse response = send("DELETE", "/v1/tasks/t-404", "");

        assertEquals(
                404,
                response.status(),
                "Отмена неизвестной задачи обязана давать 404: " + response.body()
                        + " Иначе тест delete_UnknownTask_ReturnsNotFound перестанет ловить эту поломку. При смене delete_UnknownTask_ReturnsNotFound проверь это утверждение.");
        assertTrue(
                gateway.cancels().isEmpty(),
                "404 не должен вызывать cancel у шлюза"
                        + " Иначе тест delete_UnknownTask_ReturnsNotFound перестанет ловить эту поломку. При смене delete_UnknownTask_ReturnsNotFound проверь это утверждение.");
    }

    @Test
    void delete_TerminalTask_ReturnsRecordWithoutCancel() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));
        runToState(taskId, TaskState.RUNNING, null);
        runToState(taskId, TaskState.SUCCEEDED, null);

        RestResponse response = send("DELETE", "/v1/tasks/" + taskId, "");

        assertEquals(
                200,
                response.status(),
                "DELETE завершённой задачи обязан быть идемпотентным 200: " + response.body()
                        + " Иначе тест delete_TerminalTask_ReturnsRecordWithoutCancel перестанет ловить эту поломку. При смене delete_TerminalTask_ReturnsRecordWithoutCancel проверь это утверждение.");
        assertTrue(
                response.body().contains("\"state\":\"succeeded\""),
                "Завершённая задача обязана вернуть своё состояние: " + response.body()
                        + " Иначе тест delete_TerminalTask_ReturnsRecordWithoutCancel перестанет ловить эту поломку. При смене delete_TerminalTask_ReturnsRecordWithoutCancel проверь это утверждение.");
        assertTrue(
                gateway.cancels().isEmpty(),
                "Завершённой задаче отмена не нужна"
                        + " Иначе тест delete_TerminalTask_ReturnsRecordWithoutCancel перестанет ловить эту поломку. При смене delete_TerminalTask_ReturnsRecordWithoutCancel проверь это утверждение.");
    }

    @Test
    void events_UnknownTask_ReturnsNotFound() {
        RestResponse response = send("GET", "/v1/tasks/t-404/events", "");

        assertEquals(
                404,
                response.status(),
                "SSE у неизвестной задачи обязан давать 404: " + response.body()
                        + " Иначе тест events_UnknownTask_ReturnsNotFound перестанет ловить эту поломку. При смене events_UnknownTask_ReturnsNotFound проверь это утверждение.");
        assertNull(
                response.stream(),
                "404 не должен открывать поток кадров"
                        + " Иначе тест events_UnknownTask_ReturnsNotFound перестанет ловить эту поломку. При смене events_UnknownTask_ReturnsNotFound проверь это утверждение.");
        assertTrue(
                response.body().contains("сервер перезапущен")
                        && response.body().contains("задача утрачена"),
                "SSE после перезапуска обязан объяснять потерю, а не молчать: " + response.body()
                        + " Иначе тест events_UnknownTask_ReturnsNotFound перестанет ловить эту поломку. При смене events_UnknownTask_ReturnsNotFound проверь это утверждение.");
    }

    @Test
    void events_KnownTask_OpensStreamWithStateFrame() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));

        RestResponse response = send("GET", "/v1/tasks/" + taskId + "/events", "");

        assertEquals(
                200,
                response.status(),
                "SSE обязана открываться 200: " + response.body()
                        + " Иначе тест events_KnownTask_OpensStreamWithStateFrame перестанет ловить эту поломку. При смене events_KnownTask_OpensStreamWithStateFrame проверь это утверждение.");
        assertTrue(
                response.stream() != null,
                "Ответ обязан нести поток кадров"
                        + " Иначе тест events_KnownTask_OpensStreamWithStateFrame перестанет ловить эту поломку. При смене events_KnownTask_OpensStreamWithStateFrame проверь это утверждение.");
        assertEquals(
                "text/event-stream",
                response.headers().get("Content-Type"),
                "SSE обязана отдаваться с content-type text/event-stream"
                        + " Иначе тест events_KnownTask_OpensStreamWithStateFrame перестанет ловить эту поломку. При смене events_KnownTask_OpensStreamWithStateFrame проверь это утверждение.");
        try (SseStream stream = response.stream()) {
            String frame = stream.nextFrame();
            assertTrue(
                    frame.startsWith("id: "),
                    "Первый кадр обязан нести id: " + frame
                            + " Иначе тест events_KnownTask_OpensStreamWithStateFrame перестанет ловить эту поломку. При смене events_KnownTask_OpensStreamWithStateFrame проверь это утверждение.");
            assertTrue(
                    frame.contains("event: state"),
                    "Первый кадр обязан быть state: " + frame
                            + " Иначе тест events_KnownTask_OpensStreamWithStateFrame перестанет ловить эту поломку. При смене events_KnownTask_OpensStreamWithStateFrame проверь это утверждение.");
            assertTrue(
                    frame.contains("\"state\":\"accepted\""),
                    "Первый кадр обязан нести состояние: " + frame
                            + " Иначе тест events_KnownTask_OpensStreamWithStateFrame перестанет ловить эту поломку. При смене events_KnownTask_OpensStreamWithStateFrame проверь это утверждение.");
        }
        assertEquals(
                0,
                slots.open(),
                "Закрытие потока обязано освобождать слот"
                        + " Иначе тест events_KnownTask_OpensStreamWithStateFrame перестанет ловить эту поломку. При смене events_KnownTask_OpensStreamWithStateFrame проверь это утверждение.");
    }
}
