package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardLogEntry;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static BoardLogEntry entry(String id) {
        return new BoardLogEntry(BoardLog.Event.DONE, 10, id, BoardCase.BUILD_PEN, 1, "always",
                65, 65, List.of(), null, "hash-1", 10);
    }

    private RestResponse send(String method, String path, String body) {
        return router.handle(new RestRequest(method, path, Map.of(), Map.of(), utf8(body)));
    }

    private static String body(String requestId, String extra) {
        return "{\"request_id\":\"" + requestId + "\",\"case\":\"BUILD_FENCE\"" + extra + "}";
    }

    @Test
    void get_Tasks_ReturnsQueueWithMissing() throws Exception {
        gateway.with(new BoardTask("board-a", "SUPPLY_SITE", 20, "дух-1", "WAITING_RESOURCES",
                Map.of("oak_log", 16), Map.of("oak_log", 10), List.of("board-root"), 7, 1, "always"));

        RestResponse response = send("GET", "/v1/board", "");

        assertEquals(200, response.status(), "Наблюдение обязано отвечать 200: " + response.body());
        JsonNode tasks = MAPPER.readTree(response.body()).get("tasks");
        assertEquals(1, tasks.size(), "GET обязан вернуть все задания очереди: " + response.body());
        JsonNode task = tasks.get(0);
        assertEquals("board-a", task.get("id").asText(), "GET обязан отдавать id задания: " + response.body());
        assertEquals("SUPPLY_SITE", task.get("case").asText(), "GET обязан отдавать кейс: " + response.body());
        assertEquals("WAITING_RESOURCES", task.get("status").asText(),
                "GET обязан отдавать статус задания: " + response.body());
        assertEquals(10, task.get("missing").get("oak_log").asInt(),
                "GET обязан отдавать нехватку из гейта, а не полную нужду: " + response.body());
    }

    @Test
    void post_NewCase_AcceptsAndReturnsIdAndRequestId() throws Exception {
        String requestId = uuid();

        RestResponse response = send("POST", "/v1/board/tasks", body(requestId,
                ",\"assignee\":\"" + uuid() + "\",\"requiredItems\":{\"oak_log\":16},\"level\":2,\"cause\":\"fence:1:64:2\""));

        assertEquals(202, response.status(), "Постановка задания обязана приниматься 202: " + response.body());
        JsonNode parsed = MAPPER.readTree(response.body());
        assertEquals(requestId, parsed.get("request_id").asText(),
                "202 обязан эхать request_id клиента: " + response.body());
        assertEquals("board-" + requestId, parsed.get("id").asText(),
                "id задания доска назначает от request_id: " + response.body());
        assertEquals(1, gateway.size(), "Постановка обязана создать одно задание");
    }

    @Test
    void post_ReplaySameRequestId_ReturnsSameTaskWithoutDuplicate() {
        String requestId = uuid();
        String body = body(requestId, ",\"requiredItems\":{\"oak_log\":16}");
        RestResponse first = send("POST", "/v1/board/tasks", body);

        RestResponse replay = send("POST", "/v1/board/tasks", body);

        assertEquals(202, replay.status(), "Повтор того же запроса обязан оставаться 202: " + replay.body());
        assertEquals(first.body(), replay.body(), "Идемпотентный повтор обязан вернуть исходный ответ байт в байт");
        assertEquals(1, gateway.size(), "Повтор не должен создавать второе задание");
        assertEquals(1, gateway.submits().size(), "Повтор не должен второй раз обращаться к доске");
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

        assertEquals(202, send("POST", "/v1/board/tasks", reordered).status(),
                "Повтор с другим порядком ключей обязан быть повтором: " + replay.body());
        assertEquals(1, gateway.submits().size(), "Повтор с пересериализованным телом не должен слать вторую заявку");
    }

    @Test
    void post_SameRequestIdDifferentBody_Conflicts() {
        String requestId = uuid();
        send("POST", "/v1/board/tasks", body(requestId, ",\"requiredItems\":{\"oak_log\":16}"));

        RestResponse response = send("POST", "/v1/board/tasks", body(requestId, ",\"level\":2"));

        assertEquals(409, response.status(), "Тот же request_id с другим телом обязан давать 409: "
                + response.body());
        assertTrue(response.body().contains("request_id"), "409 обязан называть request_id: " + response.body());
    }

    @Test
    void post_UnknownCase_Returns422WithAllowedCases() {
        String body = "{\"request_id\":\"" + uuid() + "\",\"case\":\"ПОЧИНИТЬ_ВСЁ\"}";

        RestResponse response = send("POST", "/v1/board/tasks", body);

        assertEquals(422, response.status(), "Неизвестный кейс обязан давать 422: " + response.body());
        assertTrue(response.body().contains("BUILD_FENCE"),
                "422 обязан перечислять допустимые кейсы: " + response.body());
        assertEquals(0, gateway.size(), "Отклонённый кейс не должен ставить задание");
    }

    @Test
    void post_MissingRequestId_Returns400() {
        RestResponse response = send("POST", "/v1/board/tasks", "{\"case\":\"BUILD_FENCE\"}");

        assertEquals(400, response.status(), "Отсутствие request_id обязано давать 400: " + response.body());
        assertTrue(response.body().contains("request_id"), "400 обязан называть request_id: " + response.body());
    }

    @Test
    void post_ZeroLevel_Returns400() {
        RestResponse response = send("POST", "/v1/board/tasks", body(uuid(), ",\"level\":0"));

        assertEquals(400, response.status(), "Нулевой уровень обязан давать 400: " + response.body());
        assertTrue(response.body().contains("level"), "400 обязан называть level: " + response.body());
    }

    @Test
    void post_LevelAndCause_AreStored() throws Exception {
        String requestId = uuid();

        RestResponse response = send("POST", "/v1/board/tasks",
                body(requestId, ",\"level\":2,\"cause\":\"fence:1:64:2\""));

        assertEquals(202, response.status(), "Постановка с уровнем и причиной обязана приниматься: "
                + response.body());
        assertEquals("fence:1:64:2", MAPPER.readTree(response.body()).get("cause").asText(),
                "202 обязан отдавать причину задания: по ней стратег сверяет актуальность");
        assertEquals(2, MAPPER.readTree(response.body()).get("level").asInt(),
                "202 обязан отдавать уровень задания: " + response.body());
    }

    @Test
    void post_UnknownField_Returns400() {
        RestResponse response = send("POST", "/v1/board/tasks", body(uuid(), ",\"foo\":1"));

        assertEquals(400, response.status(), "Неизвестное поле обязано давать 400: " + response.body());
        assertTrue(response.body().contains("'foo'"), "400 обязан называть поле: " + response.body());
    }

    @Test
    void get_LogLimit_ReturnsNewestEntriesWithinLimit() throws Exception {
        gateway.withLog(entry("old")).withLog(entry("new"));

        RestResponse response = router.handle(new RestRequest("GET", "/v1/board/log",
                Map.of("limit", "1"), Map.of(), new byte[0]));

        assertEquals(200, response.status(), "Журнал обязан отдаваться 200: " + response.body());
        JsonNode entries = MAPPER.readTree(response.body()).get("entries");
        assertEquals(1, entries.size(), "limit обязан ограничивать журнал: " + response.body());
        assertEquals("new", entries.get(0).get("ticketId").asText(),
                "Журнал отдаёт свежие записи: " + response.body());
    }

    @Test
    void get_LogUnknownQuery_Returns400NamingField() {
        RestResponse response = router.handle(new RestRequest("GET", "/v1/board/log",
                Map.of("filter", "crater"), Map.of(), new byte[0]));

        assertEquals(400, response.status(), "Неизвестный фильтр журнала обязан давать 400: " + response.body());
        assertTrue(response.body().contains("filter"), "400 обязан называть параметр: " + response.body());
    }

    @Test
    void post_RelocateMovable_AcceptsWithFromAndTo() {
        String requestId = uuid();
        String body = "{\"request_id\":\"" + requestId + "\",\"case\":\"RELOCATE_BUILDING\","
                + "\"building\":\"BUILD_PEN\",\"from\":\"10,64,-3\",\"to\":\"30,64,10\"}";

        RestResponse response = send("POST", "/v1/board/tasks", body);

        assertEquals(202, response.status(), "Перенос подвижной постройки обязан приниматься: " + response.body());
        assertEquals("BUILD_PEN", gateway.lastRequest().building().orElseThrow().name(),
                "До доски обязан дойти кейс переносимой постройки");
        var to = gateway.lastRequest().to().orElseThrow();
        assertEquals(30, to.x(), "До доски обязана дойти X-координата точки назначения");
        assertEquals(64, to.y(), "До доски обязана дойти Y-координата точки назначения");
        assertEquals(10, to.z(), "До доски обязана дойти Z-координата точки назначения");
    }

    @Test
    void post_RelocateImmovable_Returns422WithList() {
        String body = "{\"request_id\":\"" + uuid() + "\",\"case\":\"RELOCATE_BUILDING\","
                + "\"building\":\"BUILD_MINE\",\"from\":\"10,64,-3\",\"to\":\"30,64,10\"}";

        RestResponse response = send("POST", "/v1/board/tasks", body);

        assertEquals(422, response.status(), "Неподвижная постройка обязана давать 422: " + response.body());
        assertTrue(response.body().contains("BUILD_MINE"),
                "422 обязан называть неподвижные постройки: " + response.body());
        assertEquals(0, gateway.size(), "Отклонённый перенос не должен ставить задание");
    }

    @Test
    void post_RelocateWithoutPoints_Returns400() {
        String body = "{\"request_id\":\"" + uuid() + "\",\"case\":\"RELOCATE_BUILDING\","
                + "\"building\":\"BUILD_PEN\"}";

        RestResponse response = send("POST", "/v1/board/tasks", body);

        assertEquals(400, response.status(), "Перенос без from/to обязан давать 400: " + response.body());
        assertTrue(response.body().contains("from"), "400 обязан называть точку: " + response.body());
    }

    @Test
    void delete_KnownTask_CancelsAndReturnsCancelled() throws Exception {
        String requestId = uuid();
        send("POST", "/v1/board/tasks", body(requestId, ""));

        RestResponse response = send("DELETE", "/v1/board/tasks/board-" + requestId, "");

        assertEquals(200, response.status(), "Отмена обязана отвечать 200: " + response.body());
        assertEquals("CANCELLED", MAPPER.readTree(response.body()).get("status").asText(),
                "200 обязан возвращать отменённое задание: " + response.body());
        assertEquals(List.of("board-" + requestId), gateway.cancels(), "Отмена обязана дойти до доски");
    }

    @Test
    void delete_UnknownTask_ReturnsNotFound() {
        RestResponse response = send("DELETE", "/v1/board/tasks/board-404", "");

        assertEquals(404, response.status(), "Отмена неизвестного задания обязана давать 404: " + response.body());
        assertFalse(gateway.cancels().contains("board-404"), "404 не должен трогать доску отменой");
    }

    @Test
    void routes_ThreeEndpoints_AreRegistered() {
        Set<String> keys = new HashSet<>();
        new BoardRoutes(gateway, idempotency).routes().forEach(route -> keys.add(route.method() + " " + route.pattern()));

        assertEquals(Set.of("GET /v1/board", "GET /v1/board/log", "POST /v1/board/tasks",
                        "DELETE /v1/board/tasks/{id}"), keys,
                "Чеклист п.21 добавляет к трём маршрутам доски наблюдение журнала");
    }
}
