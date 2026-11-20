package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.board.api.BoardCase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoardRoutesTestsPart2 {
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
        return new BoardLogEntry(
                BoardLog.Event.DONE, 10, id, BoardCase.BUILD_PEN, 1, "always", 65, 65, List.of(), null, "hash-1", 10);
    }

    private RestResponse send(String method, String path, String body) {
        return router.handle(new RestRequest(method, path, Map.of(), Map.of(), utf8(body)));
    }

    private static String body(String requestId, String extra) {
        return "{\"request_id\":\"" + requestId + "\",\"case\":\"BUILD_FENCE\"" + extra + "}";
    }

    @Test
    void post_LevelAndCause_AreStored() throws Exception {
        String requestId = uuid();

        RestResponse response =
                send("POST", "/v1/board/tasks", body(requestId, ",\"level\":2,\"cause\":\"fence:1:64:2\""));

        assertEquals(
                202,
                response.status(),
                "Постановка с уровнем и причиной обязана приниматься: " + response.body()
                        + " Иначе тест post_LevelAndCause_AreStored перестанет ловить эту поломку. При смене post_LevelAndCause_AreStored проверь это утверждение.");
        assertEquals(
                "fence:1:64:2",
                MAPPER.readTree(response.body()).get("cause").asText(),
                "202 обязан отдавать причину задания: по ней стратег сверяет актуальность"
                        + " Иначе тест post_LevelAndCause_AreStored перестанет ловить эту поломку. При смене post_LevelAndCause_AreStored проверь это утверждение.");
        assertEquals(
                2,
                MAPPER.readTree(response.body()).get("level").asInt(),
                "202 обязан отдавать уровень задания: " + response.body()
                        + " Иначе тест post_LevelAndCause_AreStored перестанет ловить эту поломку. При смене post_LevelAndCause_AreStored проверь это утверждение.");
    }

    @Test
    void post_UnknownField_Returns400() {
        RestResponse response = send("POST", "/v1/board/tasks", body(uuid(), ",\"foo\":1"));

        assertEquals(
                400,
                response.status(),
                "Неизвестное поле обязано давать 400: " + response.body()
                        + " Иначе тест post_UnknownField_Returns400 перестанет ловить эту поломку. При смене post_UnknownField_Returns400 проверь это утверждение.");
        assertTrue(
                response.body().contains("'foo'"),
                "400 обязан называть поле: " + response.body()
                        + " Иначе тест post_UnknownField_Returns400 перестанет ловить эту поломку. При смене post_UnknownField_Returns400 проверь это утверждение.");
    }

    @Test
    void get_LogLimit_ReturnsNewestEntriesWithinLimit() throws Exception {
        gateway.withLog(entry("old")).withLog(entry("new"));

        RestResponse response =
                router.handle(new RestRequest("GET", "/v1/board/log", Map.of("limit", "1"), Map.of(), new byte[0]));

        assertEquals(
                200,
                response.status(),
                "Журнал обязан отдаваться 200: " + response.body()
                        + " Иначе тест get_LogLimit_ReturnsNewestEntriesWithinLimit перестанет ловить эту поломку. При смене get_LogLimit_ReturnsNewestEntriesWithinLimit проверь это утверждение.");
        JsonNode entries = MAPPER.readTree(response.body()).get("entries");
        assertEquals(
                1,
                entries.size(),
                "limit обязан ограничивать журнал: " + response.body()
                        + " Иначе тест get_LogLimit_ReturnsNewestEntriesWithinLimit перестанет ловить эту поломку. При смене get_LogLimit_ReturnsNewestEntriesWithinLimit проверь это утверждение.");
        assertEquals(
                "new",
                entries.get(0).get("ticketId").asText(),
                "Журнал отдаёт свежие записи: " + response.body()
                        + " Иначе тест get_LogLimit_ReturnsNewestEntriesWithinLimit перестанет ловить эту поломку. При смене get_LogLimit_ReturnsNewestEntriesWithinLimit проверь это утверждение.");
    }

    @Test
    void get_LogUnknownQuery_Returns400NamingField() {
        RestResponse response = router.handle(
                new RestRequest("GET", "/v1/board/log", Map.of("filter", "crater"), Map.of(), new byte[0]));

        assertEquals(
                400,
                response.status(),
                "Неизвестный фильтр журнала обязан давать 400: " + response.body()
                        + " Иначе тест get_LogUnknownQuery_Returns400NamingField перестанет ловить эту поломку. При смене get_LogUnknownQuery_Returns400NamingField проверь это утверждение.");
        assertTrue(
                response.body().contains("filter"),
                "400 обязан называть параметр: " + response.body()
                        + " Иначе тест get_LogUnknownQuery_Returns400NamingField перестанет ловить эту поломку. При смене get_LogUnknownQuery_Returns400NamingField проверь это утверждение.");
    }

    @Test
    void post_RelocateMovable_AcceptsWithFromAndTo() {
        String requestId = uuid();
        String body = "{\"request_id\":\"" + requestId + "\",\"case\":\"RELOCATE_BUILDING\","
                + "\"building\":\"BUILD_PEN\",\"from\":\"10,64,-3\",\"to\":\"30,64,10\"}";

        RestResponse response = send("POST", "/v1/board/tasks", body);

        assertEquals(
                202,
                response.status(),
                "Перенос подвижной постройки обязан приниматься: " + response.body()
                        + " Иначе тест post_RelocateMovable_AcceptsWithFromAndTo перестанет ловить эту поломку. При смене post_RelocateMovable_AcceptsWithFromAndTo проверь это утверждение.");
        assertEquals(
                "BUILD_PEN",
                gateway.lastRequest().building().orElseThrow().name(),
                "До доски обязан дойти кейс переносимой постройки"
                        + " Иначе тест post_RelocateMovable_AcceptsWithFromAndTo перестанет ловить эту поломку. При смене post_RelocateMovable_AcceptsWithFromAndTo проверь это утверждение.");
        var to = gateway.lastRequest().to().orElseThrow();
        assertEquals(
                30,
                to.x(),
                "До доски обязана дойти X-координата точки назначения"
                        + " Иначе тест post_RelocateMovable_AcceptsWithFromAndTo перестанет ловить эту поломку. При смене post_RelocateMovable_AcceptsWithFromAndTo проверь это утверждение.");
        assertEquals(
                64,
                to.y(),
                "До доски обязана дойти Y-координата точки назначения"
                        + " Иначе тест post_RelocateMovable_AcceptsWithFromAndTo перестанет ловить эту поломку. При смене post_RelocateMovable_AcceptsWithFromAndTo проверь это утверждение.");
        assertEquals(
                10,
                to.z(),
                "До доски обязана дойти Z-координата точки назначения"
                        + " Иначе тест post_RelocateMovable_AcceptsWithFromAndTo перестанет ловить эту поломку. При смене post_RelocateMovable_AcceptsWithFromAndTo проверь это утверждение.");
    }

    @Test
    void post_RelocateImmovable_Returns422WithList() {
        String body = "{\"request_id\":\"" + uuid() + "\",\"case\":\"RELOCATE_BUILDING\","
                + "\"building\":\"BUILD_MINE\",\"from\":\"10,64,-3\",\"to\":\"30,64,10\"}";

        RestResponse response = send("POST", "/v1/board/tasks", body);

        assertEquals(
                422,
                response.status(),
                "Неподвижная постройка обязана давать 422: " + response.body()
                        + " Иначе тест post_RelocateImmovable_Returns422WithList перестанет ловить эту поломку. При смене post_RelocateImmovable_Returns422WithList проверь это утверждение.");
        assertTrue(
                response.body().contains("BUILD_MINE"),
                "422 обязан называть неподвижные постройки: " + response.body()
                        + " Иначе тест post_RelocateImmovable_Returns422WithList перестанет ловить эту поломку. При смене post_RelocateImmovable_Returns422WithList проверь это утверждение.");
        assertEquals(
                0,
                gateway.size(),
                "Отклонённый перенос не должен ставить задание"
                        + " Иначе тест post_RelocateImmovable_Returns422WithList перестанет ловить эту поломку. При смене post_RelocateImmovable_Returns422WithList проверь это утверждение.");
    }

    @Test
    void post_RelocationFieldOnOtherCase_Returns400NamingThatField() {
        RestResponse response = send("POST", "/v1/board/tasks", body(uuid(), ",\"to\":\"30,64,10\""));

        assertEquals(
                400,
                response.status(),
                "Поле переноса у другого кейса обязано давать 400: " + response.body()
                        + " Иначе тест post_RelocationFieldOnOtherCase_Returns400NamingThatField перестанет ловить эту поломку. При смене post_RelocationFieldOnOtherCase_Returns400NamingThatField проверь это утверждение.");
        assertTrue(
                response.body().contains("\"field\":\"to\""),
                "400 обязан называть лишнее поле, которое прислал клиент (to), а не первое из списка: "
                        + response.body()
                        + " Иначе тест post_RelocationFieldOnOtherCase_Returns400NamingThatField перестанет ловить эту поломку. При смене post_RelocationFieldOnOtherCase_Returns400NamingThatField проверь это утверждение.");
    }

    @Test
    void post_RelocatePointWithTrailingComma_Returns400() {
        String body = "{\"request_id\":\"" + uuid() + "\",\"case\":\"RELOCATE_BUILDING\","
                + "\"building\":\"BUILD_PEN\",\"from\":\"10,64,-3,\",\"to\":\"30,64,10\"}";

        RestResponse response = send("POST", "/v1/board/tasks", body);

        assertEquals(
                400,
                response.status(),
                "Точка «10,64,-3,» с хвостовой запятой — не тройка координат, а опечатка: " + response.body()
                        + " Иначе тест post_RelocatePointWithTrailingComma_Returns400 перестанет ловить эту поломку. При смене post_RelocatePointWithTrailingComma_Returns400 проверь это утверждение.");
        assertEquals(
                0,
                gateway.size(),
                "Отклонённый перенос не должен ставить задание"
                        + " Иначе тест post_RelocatePointWithTrailingComma_Returns400 перестанет ловить эту поломку. При смене post_RelocatePointWithTrailingComma_Returns400 проверь это утверждение.");
    }

    @Test
    void post_RelocateWithoutPoints_Returns400() {
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"case\":\"RELOCATE_BUILDING\"," + "\"building\":\"BUILD_PEN\"}";

        RestResponse response = send("POST", "/v1/board/tasks", body);

        assertEquals(
                400,
                response.status(),
                "Перенос без from/to обязан давать 400: " + response.body()
                        + " Иначе тест post_RelocateWithoutPoints_Returns400 перестанет ловить эту поломку. При смене post_RelocateWithoutPoints_Returns400 проверь это утверждение.");
        assertTrue(
                response.body().contains("from"),
                "400 обязан называть точку: " + response.body()
                        + " Иначе тест post_RelocateWithoutPoints_Returns400 перестанет ловить эту поломку. При смене post_RelocateWithoutPoints_Returns400 проверь это утверждение.");
    }

    @Test
    void delete_KnownTask_CancelsAndReturnsCancelled() throws Exception {
        String requestId = uuid();
        send("POST", "/v1/board/tasks", body(requestId, ""));

        RestResponse response = send("DELETE", "/v1/board/tasks/board-" + requestId, "");

        assertEquals(
                200,
                response.status(),
                "Отмена обязана отвечать 200: " + response.body()
                        + " Иначе тест delete_KnownTask_CancelsAndReturnsCancelled перестанет ловить эту поломку. При смене delete_KnownTask_CancelsAndReturnsCancelled проверь это утверждение.");
        assertEquals(
                "CANCELLED",
                MAPPER.readTree(response.body()).get("status").asText(),
                "200 обязан возвращать отменённое задание: " + response.body()
                        + " Иначе тест delete_KnownTask_CancelsAndReturnsCancelled перестанет ловить эту поломку. При смене delete_KnownTask_CancelsAndReturnsCancelled проверь это утверждение.");
        assertEquals(
                List.of("board-" + requestId),
                gateway.cancels(),
                "Отмена обязана дойти до доски"
                        + " Иначе тест delete_KnownTask_CancelsAndReturnsCancelled перестанет ловить эту поломку. При смене delete_KnownTask_CancelsAndReturnsCancelled проверь это утверждение.");
    }

    @Test
    void delete_ColonInId_FindsTaskBothRawAndPercentEncoded() {
        String id = "first:clear_grounds";
        gateway.with(new BoardTask(
                id, "CLEAR_GROUNDS", 50, null, "ASSIGNED", Map.of(), Map.of(), List.of(), 7, 1, "always"));

        RestResponse raw = send("DELETE", "/v1/board/tasks/" + id, "");
        RestResponse encoded = send("DELETE", "/v1/board/tasks/first%3Aclear_grounds", "");

        assertEquals(
                200,
                raw.status(),
                "Отмена задания с двоеточием обязана находить его: " + raw.body()
                        + " Иначе тест delete_ColonInId_FindsTaskBothRawAndPercentEncoded перестанет ловить эту поломку. При смене delete_ColonInId_FindsTaskBothRawAndPercentEncoded проверь это утверждение.");
        assertEquals(
                200,
                encoded.status(),
                "Тот же id в percent-кодировке (%3A) обязан находить задание: " + encoded.body()
                        + " Иначе тест delete_ColonInId_FindsTaskBothRawAndPercentEncoded перестанет ловить эту поломку. При смене delete_ColonInId_FindsTaskBothRawAndPercentEncoded проверь это утверждение.");
        assertEquals(
                List.of(id, id),
                gateway.cancels(),
                "Обе формы id дошли до доски: иначе тикет нельзя снять, хотя GET /v1/board его показывает"
                        + " При смене delete_ColonInId_FindsTaskBothRawAndPercentEncoded проверь это утверждение.");
    }
}
