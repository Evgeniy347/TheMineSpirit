package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoardRoutesTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FakeBoardGateway gateway;
    private IdempotencyCache idempotency;
    private Router router;

    @BeforeEach
    void setUp() {
        gateway = new FakeBoardGateway();
        idempotency = new IdempotencyCache();
        router = Router.of(new BoardRoutes(gateway, idempotency).routes());
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String body(String requestId, String extra) {
        return "{\"request_id\":\"" + requestId + "\",\"case\":\"BUILD_FENCE\"" + extra + "}";
    }

    private RestResponse send(String method, String path, String body) {
        return router.handle(new RestRequest(method, path, Map.of(), Map.of(), utf8(body)));
    }

    @Test
    void get_Tasks_ReturnsQueueWithMissing() throws Exception {
        gateway.with(new BoardTask(
                "board-a",
                "SUPPLY_SITE",
                20,
                "дух-1",
                "WAITING_RESOURCES",
                Map.of("oak_log", 16),
                Map.of("oak_log", 10),
                List.of("board-root"),
                7,
                1,
                "always"));

        RestResponse response = send("GET", "/v1/board", "");

        assertEquals(
                200,
                response.status(),
                "Наблюдение обязано отвечать 200: " + response.body()
                        + " Иначе тест get_Tasks_ReturnsQueueWithMissing перестанет ловить эту поломку. При смене get_Tasks_ReturnsQueueWithMissing проверь это утверждение.");
        JsonNode tasks = MAPPER.readTree(response.body()).get("tasks");
        assertEquals(
                1,
                tasks.size(),
                "GET обязан вернуть все задания очереди: " + response.body()
                        + " Иначе тест get_Tasks_ReturnsQueueWithMissing перестанет ловить эту поломку. При смене get_Tasks_ReturnsQueueWithMissing проверь это утверждение.");
        JsonNode task = tasks.get(0);
        assertEquals(
                "board-a",
                task.get("id").asText(),
                "GET обязан отдавать id задания: " + response.body()
                        + " Иначе тест get_Tasks_ReturnsQueueWithMissing перестанет ловить эту поломку. При смене get_Tasks_ReturnsQueueWithMissing проверь это утверждение.");
        assertEquals(
                "SUPPLY_SITE",
                task.get("case").asText(),
                "GET обязан отдавать кейс: " + response.body()
                        + " Иначе тест get_Tasks_ReturnsQueueWithMissing перестанет ловить эту поломку. При смене get_Tasks_ReturnsQueueWithMissing проверь это утверждение.");
        assertEquals(
                "WAITING_RESOURCES",
                task.get("status").asText(),
                "GET обязан отдавать статус задания: " + response.body()
                        + " Иначе тест get_Tasks_ReturnsQueueWithMissing перестанет ловить эту поломку. При смене get_Tasks_ReturnsQueueWithMissing проверь это утверждение.");
        assertEquals(
                10,
                task.get("missing").get("oak_log").asInt(),
                "GET обязан отдавать нехватку из гейта, а не полную нужду: " + response.body()
                        + " Иначе тест get_Tasks_ReturnsQueueWithMissing перестанет ловить эту поломку. При смене get_Tasks_ReturnsQueueWithMissing проверь это утверждение.");
        assertEquals(
                0L,
                task.get("startedAt").asLong(),
                "GET обязан отдавать тик выдачи задания: " + response.body()
                        + " Иначе тест get_Tasks_ReturnsQueueWithMissing перестанет ловить эту поломку. При смене get_Tasks_ReturnsQueueWithMissing проверь это утверждение.");
        assertEquals(
                0L,
                task.get("finishedAt").asLong(),
                "GET обязан отдавать тик закрытия задания: " + response.body()
                        + " Иначе тест get_Tasks_ReturnsQueueWithMissing перестанет ловить эту поломку. При смене get_Tasks_ReturnsQueueWithMissing проверь это утверждение.");
        assertEquals(
                0L,
                task.get("durationTicks").asLong(),
                "GET обязан отдавать затраченные тики задания: " + response.body()
                        + " Иначе тест get_Tasks_ReturnsQueueWithMissing перестанет ловить эту поломку. При смене get_Tasks_ReturnsQueueWithMissing проверь это утверждение.");
    }

    @Test
    void post_NewCase_AcceptsAndReturnsIdAndRequestId() throws Exception {
        String requestId = uuid();

        RestResponse response = send(
                "POST",
                "/v1/board/tasks",
                body(
                        requestId,
                        ",\"assignee\":\"" + uuid()
                                + "\",\"requiredItems\":{\"oak_log\":16},\"level\":2,\"cause\":\"fence:1:64:2\""));

        assertEquals(
                202,
                response.status(),
                "Постановка задания обязана приниматься 202: " + response.body()
                        + " Иначе тест post_NewCase_AcceptsAndReturnsIdAndRequestId перестанет ловить эту поломку. При смене post_NewCase_AcceptsAndReturnsIdAndRequestId проверь это утверждение.");
        JsonNode parsed = MAPPER.readTree(response.body());
        assertEquals(
                requestId,
                parsed.get("request_id").asText(),
                "202 обязан эхать request_id клиента: " + response.body()
                        + " Иначе тест post_NewCase_AcceptsAndReturnsIdAndRequestId перестанет ловить эту поломку. При смене post_NewCase_AcceptsAndReturnsIdAndRequestId проверь это утверждение.");
        assertEquals(
                "board-" + requestId,
                parsed.get("id").asText(),
                "id задания доска назначает от request_id: " + response.body()
                        + " Иначе тест post_NewCase_AcceptsAndReturnsIdAndRequestId перестанет ловить эту поломку. При смене post_NewCase_AcceptsAndReturnsIdAndRequestId проверь это утверждение.");
        assertEquals(
                1,
                gateway.size(),
                "Постановка обязана создать одно задание"
                        + " Иначе тест post_NewCase_AcceptsAndReturnsIdAndRequestId перестанет ловить эту поломку. При смене post_NewCase_AcceptsAndReturnsIdAndRequestId проверь это утверждение.");
    }

    @Test
    void post_ReplaySameRequestId_ReturnsSameTaskWithoutDuplicate() {
        String requestId = uuid();
        String body = body(requestId, ",\"requiredItems\":{\"oak_log\":16}");
        RestResponse first = send("POST", "/v1/board/tasks", body);

        RestResponse replay = send("POST", "/v1/board/tasks", body);

        assertEquals(
                202,
                replay.status(),
                "Повтор того же запроса обязан оставаться 202: " + replay.body()
                        + " Иначе тест post_ReplaySameRequestId_ReturnsSameTaskWithoutDuplicate перестанет ловить эту поломку. При смене post_ReplaySameRequestId_ReturnsSameTaskWithoutDuplicate проверь это утверждение.");
        assertEquals(
                first.body(),
                replay.body(),
                "Идемпотентный повтор обязан вернуть исходный ответ байт в байт"
                        + " Иначе тест post_ReplaySameRequestId_ReturnsSameTaskWithoutDuplicate перестанет ловить эту поломку. При смене post_ReplaySameRequestId_ReturnsSameTaskWithoutDuplicate проверь это утверждение.");
        assertEquals(
                1,
                gateway.size(),
                "Повтор не должен создавать второе задание"
                        + " Иначе тест post_ReplaySameRequestId_ReturnsSameTaskWithoutDuplicate перестанет ловить эту поломку. При смене post_ReplaySameRequestId_ReturnsSameTaskWithoutDuplicate проверь это утверждение.");
        assertEquals(
                1,
                gateway.submits().size(),
                "Повтор не должен второй раз обращаться к доске"
                        + " Иначе тест post_ReplaySameRequestId_ReturnsSameTaskWithoutDuplicate перестанет ловить эту поломку. При смене post_ReplaySameRequestId_ReturnsSameTaskWithoutDuplicate проверь это утверждение.");
    }

    @Test
    void post_ReplayWithReorderedItems_ReturnsSameTaskNotConflict() {
        String requestId = uuid();
        String first = "{\"request_id\":\"" + requestId + "\",\"case\":\"BUILD_FENCE\","
                + "\"requiredItems\":{\"oak_log\":16,\"dirt\":4}}";
        String reordered = "{\"requiredItems\":{\"dirt\":4,\"oak_log\":16},\"case\":\"BUILD_FENCE\","
                + "\"request_id\":\"" + requestId + "\"}";

        RestResponse replay = send("POST", "/v1/board/tasks", reordered);
        send("POST", "/v1/board/tasks", first);

        assertEquals(
                202,
                send("POST", "/v1/board/tasks", reordered).status(),
                "Повтор с другим порядком ключей обязан быть повтором: " + replay.body()
                        + " Иначе тест post_ReplayWithReorderedItems_ReturnsSameTaskNotConflict перестанет ловить эту поломку. При смене post_ReplayWithReorderedItems_ReturnsSameTaskNotConflict проверь это утверждение.");
        assertEquals(
                1,
                gateway.submits().size(),
                "Повтор с пересериализованным телом не должен слать вторую заявку"
                        + " Иначе тест post_ReplayWithReorderedItems_ReturnsSameTaskNotConflict перестанет ловить эту поломку. При смене post_ReplayWithReorderedItems_ReturnsSameTaskNotConflict проверь это утверждение.");
    }

    @Test
    void post_SameRequestIdDifferentBody_Conflicts() {
        String requestId = uuid();
        send("POST", "/v1/board/tasks", body(requestId, ",\"requiredItems\":{\"oak_log\":16}"));

        RestResponse response = send("POST", "/v1/board/tasks", body(requestId, ",\"level\":2"));

        assertEquals(
                409,
                response.status(),
                "Тот же request_id с другим телом обязан давать 409: " + response.body()
                        + " Иначе тест post_SameRequestIdDifferentBody_Conflicts перестанет ловить эту поломку. При смене post_SameRequestIdDifferentBody_Conflicts проверь это утверждение.");
        assertTrue(
                response.body().contains("request_id"),
                "409 обязан называть request_id: " + response.body()
                        + " Иначе тест post_SameRequestIdDifferentBody_Conflicts перестанет ловить эту поломку. При смене post_SameRequestIdDifferentBody_Conflicts проверь это утверждение.");
    }

    @Test
    void post_UnknownCase_Returns422WithAllowedCases() {
        String body = "{\"request_id\":\"" + uuid() + "\",\"case\":\"ПОЧИНИТЬ_ВСЁ\"}";

        RestResponse response = send("POST", "/v1/board/tasks", body);

        assertEquals(
                422,
                response.status(),
                "Неизвестный кейс обязан давать 422: " + response.body()
                        + " Иначе тест post_UnknownCase_Returns422WithAllowedCases перестанет ловить эту поломку. При смене post_UnknownCase_Returns422WithAllowedCases проверь это утверждение.");
        assertTrue(
                response.body().contains("BUILD_FENCE"),
                "422 обязан перечислять допустимые кейсы: " + response.body()
                        + " Иначе тест post_UnknownCase_Returns422WithAllowedCases перестанет ловить эту поломку. При смене post_UnknownCase_Returns422WithAllowedCases проверь это утверждение.");
        assertEquals(
                0,
                gateway.size(),
                "Отклонённый кейс не должен ставить задание"
                        + " Иначе тест post_UnknownCase_Returns422WithAllowedCases перестанет ловить эту поломку. При смене post_UnknownCase_Returns422WithAllowedCases проверь это утверждение.");
    }

    @Test
    void post_MissingRequestId_Returns400() {
        RestResponse response = send("POST", "/v1/board/tasks", "{\"case\":\"BUILD_FENCE\"}");

        assertEquals(
                400,
                response.status(),
                "Отсутствие request_id обязано давать 400: " + response.body()
                        + " Иначе тест post_MissingRequestId_Returns400 перестанет ловить эту поломку. При смене post_MissingRequestId_Returns400 проверь это утверждение.");
        assertTrue(
                response.body().contains("request_id"),
                "400 обязан называть request_id: " + response.body()
                        + " Иначе тест post_MissingRequestId_Returns400 перестанет ловить эту поломку. При смене post_MissingRequestId_Returns400 проверь это утверждение.");
    }

    @Test
    void post_ZeroLevel_Returns400() {
        RestResponse response = send("POST", "/v1/board/tasks", body(uuid(), ",\"level\":0"));

        assertEquals(
                400,
                response.status(),
                "Нулевой уровень обязан давать 400: " + response.body()
                        + " Иначе тест post_ZeroLevel_Returns400 перестанет ловить эту поломку. При смене post_ZeroLevel_Returns400 проверь это утверждение.");
        assertTrue(
                response.body().contains("level"),
                "400 обязан называть level: " + response.body()
                        + " Иначе тест post_ZeroLevel_Returns400 перестанет ловить эту поломку. При смене post_ZeroLevel_Returns400 проверь это утверждение.");
    }
}
