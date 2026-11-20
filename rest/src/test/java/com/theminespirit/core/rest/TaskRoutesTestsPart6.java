package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class TaskRoutesTestsPart6 {
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
    void get_KnownTask_ReturnsStateAndBot() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));

        RestResponse response = send("GET", "/v1/tasks/" + taskId, "");

        assertEquals(
                200,
                response.status(),
                "Известная задача обязана читаться: " + response.body()
                        + " Иначе тест get_KnownTask_ReturnsStateAndBot перестанет ловить эту поломку. При смене get_KnownTask_ReturnsStateAndBot проверь это утверждение.");
        assertTrue(
                response.body().contains("\"state\":\"accepted\""),
                "GET обязан отдавать состояние задачи: " + response.body()
                        + " Иначе тест get_KnownTask_ReturnsStateAndBot перестанет ловить эту поломку. При смене get_KnownTask_ReturnsStateAndBot проверь это утверждение.");
        assertTrue(
                response.body().contains(BOT),
                "GET обязан отдавать духа: " + response.body()
                        + " Иначе тест get_KnownTask_ReturnsStateAndBot перестанет ловить эту поломку. При смене get_KnownTask_ReturnsStateAndBot проверь это утверждение.");
    }

    @Test
    void get_UnknownTask_ReturnsNotFound() {
        RestResponse response = send("GET", "/v1/tasks/t-404", "");

        assertEquals(
                404,
                response.status(),
                "Неизвестная задача обязана давать 404: " + response.body()
                        + " Иначе тест get_UnknownTask_ReturnsNotFound перестанет ловить эту поломку. При смене get_UnknownTask_ReturnsNotFound проверь это утверждение.");
        assertTrue(
                response.body().contains("t-404"),
                "404 обязан называть task_id: " + response.body()
                        + " Иначе тест get_UnknownTask_ReturnsNotFound перестанет ловить эту поломку. При смене get_UnknownTask_ReturnsNotFound проверь это утверждение.");
        assertTrue(
                response.body().contains("сервер перезапущен")
                        && response.body().contains("задача утрачена"),
                "404 после перезапуска обязан объяснять потерю, а не молчать: " + response.body()
                        + " Иначе тест get_UnknownTask_ReturnsNotFound перестанет ловить эту поломку. При смене get_UnknownTask_ReturnsNotFound проверь это утверждение.");
    }

    @Test
    void delete_ActiveTask_CancelsViaGatewayAndRegistry() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));

        RestResponse response = send("DELETE", "/v1/tasks/" + taskId, "");

        assertEquals(
                200,
                response.status(),
                "Отмена обязана отвечать 200: " + response.body()
                        + " Иначе тест delete_ActiveTask_CancelsViaGatewayAndRegistry перестанет ловить эту поломку. При смене delete_ActiveTask_CancelsViaGatewayAndRegistry проверь это утверждение.");
        assertTrue(
                response.body().contains("\"state\":\"cancelled\""),
                "200 обязан возвращать cancelled: " + response.body()
                        + " Иначе тест delete_ActiveTask_CancelsViaGatewayAndRegistry перестанет ловить эту поломку. При смене delete_ActiveTask_CancelsViaGatewayAndRegistry проверь это утверждение.");
        assertEquals(
                List.of(taskId),
                gateway.cancels(),
                "Активная задача обязана останавливать дух через шлюз"
                        + " Иначе тест delete_ActiveTask_CancelsViaGatewayAndRegistry перестанет ловить эту поломку. При смене delete_ActiveTask_CancelsViaGatewayAndRegistry проверь это утверждение.");
        assertEquals(
                TaskState.CANCELLED,
                registry.get(taskId).orElseThrow().state(),
                "Реестр обязан перейти в cancelled"
                        + " Иначе тест delete_ActiveTask_CancelsViaGatewayAndRegistry перестанет ловить эту поломку. При смене delete_ActiveTask_CancelsViaGatewayAndRegistry проверь это утверждение.");
    }

    @Test
    void delete_Twice_ReturnsCancelledWithoutSecondCancel() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));

        RestResponse first = send("DELETE", "/v1/tasks/" + taskId, "");
        RestResponse second = send("DELETE", "/v1/tasks/" + taskId, "");

        assertEquals(
                200,
                second.status(),
                "Повторный DELETE обязан оставаться идемпотентным 200: " + second.body()
                        + " Иначе тест delete_Twice_ReturnsCancelledWithoutSecondCancel перестанет ловить эту поломку. При смене delete_Twice_ReturnsCancelledWithoutSecondCancel проверь это утверждение.");
        assertEquals(
                first.body(),
                second.body(),
                "Повторный DELETE обязан возвращать ту же запись задачи"
                        + " Иначе тест delete_Twice_ReturnsCancelledWithoutSecondCancel перестанет ловить эту поломку. При смене delete_Twice_ReturnsCancelledWithoutSecondCancel проверь это утверждение.");
        assertEquals(
                List.of(taskId),
                gateway.cancels(),
                "Идемпотентный повтор DELETE не должен второй раз трогать духа"
                        + " Иначе тест delete_Twice_ReturnsCancelledWithoutSecondCancel перестанет ловить эту поломку. При смене delete_Twice_ReturnsCancelledWithoutSecondCancel проверь это утверждение.");
    }

    @Test
    void delete_GatewayFinishesTaskDuringCancel_ReturnsCurrentRecordWithoutIllegalTransition() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));
        gateway.runsOnCancel(() -> registry.observe(taskId, TaskState.SUCCEEDED, null, null));

        RestResponse response = send("DELETE", "/v1/tasks/" + taskId, "");

        assertEquals(
                200,
                response.status(),
                "Между чтением записи и отменой watcher мог успеть закрыть задачу: DELETE обязан вернуть текущую "
                        + "запись, а не упасть недопустимым переходом cancelled, который сервер отдаёт как 500: "
                        + response.body()
                        + " Иначе тест delete_GatewayFinishesTaskDuringCancel_ReturnsCurrentRecordWithoutIllegalTransition перестанет ловить эту поломку. При смене delete_GatewayFinishesTaskDuringCancel_ReturnsCurrentRecordWithoutIllegalTransition проверь это утверждение.");
        assertTrue(
                response.body().contains("\"state\":\"succeeded\""),
                "Ответ обязан нести текущее состояние задачи, а не отменённую копию: " + response.body()
                        + " Иначе тест delete_GatewayFinishesTaskDuringCancel_ReturnsCurrentRecordWithoutIllegalTransition перестанет ловить эту поломку. При смене delete_GatewayFinishesTaskDuringCancel_ReturnsCurrentRecordWithoutIllegalTransition проверь это утверждение.");
    }
}
