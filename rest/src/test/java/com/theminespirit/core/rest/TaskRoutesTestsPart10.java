package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.api.ConditionCatalog;
import com.theminespirit.core.script.api.ScriptCatalog;
import com.theminespirit.core.task.TaskRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

class TaskRoutesTestsPart10 {
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
    void plan_UnknownScript_ReturnsInvalidPlan() {
        RestResponse response = send("POST", "/v1/plan", "{\"plan\":{\"type\":\"script\",\"script\":\"return_home\"}}");

        assertEquals(
                422,
                response.status(),
                "Неизвестный скрипт обязан давать 422: " + response.body()
                        + " Иначе тест plan_UnknownScript_ReturnsInvalidPlan перестанет ловить эту поломку. При смене plan_UnknownScript_ReturnsInvalidPlan проверь это утверждение.");
        assertTrue(
                response.body().contains("return_home"),
                "422 обязан называть скрипт: " + response.body()
                        + " Иначе тест plan_UnknownScript_ReturnsInvalidPlan перестанет ловить эту поломку. При смене plan_UnknownScript_ReturnsInvalidPlan проверь это утверждение.");
        assertTrue(
                gateway.starts().isEmpty(),
                "Сухой прогон при 422 не должен стартовать духа"
                        + " Иначе тест plan_UnknownScript_ReturnsInvalidPlan перестанет ловить эту поломку. При смене plan_UnknownScript_ReturnsInvalidPlan проверь это утверждение.");
    }

    @Test
    void plan_MissingField_ReturnsBadRequest() {
        RestResponse response = send("POST", "/v1/plan", "{}");

        assertEquals(
                400,
                response.status(),
                "Отсутствие поля plan обязано давать 400: " + response.body()
                        + " Иначе тест plan_MissingField_ReturnsBadRequest перестанет ловить эту поломку. При смене plan_MissingField_ReturnsBadRequest проверь это утверждение.");
        assertTrue(
                response.body().contains("'plan'"),
                "400 обязан называть отсутствующее поле: " + response.body()
                        + " Иначе тест plan_MissingField_ReturnsBadRequest перестанет ловить эту поломку. При смене plan_MissingField_ReturnsBadRequest проверь это утверждение.");
    }

    @Test
    void create_AcceptedResponse_EchoesRequestIdAndTaskId() throws Exception {
        String requestId = uuid();
        String body =
                "{\"request_id\":\"" + requestId + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        JsonNode parsed = MAPPER.readTree(response.body());
        assertEquals(
                requestId,
                parsed.get("request_id").asText(),
                "Ответ 202 обязан эхать request_id клиента: " + response.body()
                        + " Иначе тест create_AcceptedResponse_EchoesRequestIdAndTaskId перестанет ловить эту поломку. При смене create_AcceptedResponse_EchoesRequestIdAndTaskId проверь это утверждение.");
        assertTrue(
                parsed.get("task_id").asText().startsWith("t-"),
                "Ответ 202 обязан содержать task_id в формате t-N: " + response.body()
                        + " Иначе тест create_AcceptedResponse_EchoesRequestIdAndTaskId перестанет ловить эту поломку. При смене create_AcceptedResponse_EchoesRequestIdAndTaskId проверь это утверждение.");
    }

    @Test
    void routes_UnknownPath_ReturnsNotFound() {
        RestResponse response = send("GET", "/v1/tasks", "");

        assertEquals(
                404,
                response.status(),
                "Необъявленный путь обязан давать 404: " + response.body()
                        + " Иначе тест routes_UnknownPath_ReturnsNotFound перестанет ловить эту поломку. При смене routes_UnknownPath_ReturnsNotFound проверь это утверждение.");
        assertFalse(
                response.body().contains("stream"),
                "404 не должен открывать поток"
                        + " Иначе тест routes_UnknownPath_ReturnsNotFound перестанет ловить эту поломку. При смене routes_UnknownPath_ReturnsNotFound проверь это утверждение.");
    }
}
