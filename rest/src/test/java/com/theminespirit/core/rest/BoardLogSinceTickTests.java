package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoardLogSinceTickTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FakeBoardGateway gateway;
    private Router router;

    @BeforeEach
    void setUp() {
        gateway = new FakeBoardGateway();
        router = Router.of(new BoardRoutes(gateway, new IdempotencyCache()).routes());
    }

    @Test
    void get_LogSinceTick_ReturnsOnlyNewerEntriesAndEchoesNextTick() throws Exception {
        gateway.withLog(entry("old", 10)).withLog(entry("middle", 20)).withLog(entry("new", 30));

        RestResponse response = router.handle(
                new RestRequest("GET", "/v1/board/log", Map.of("sinceTick", "15"), Map.of(), new byte[0]));

        JsonNode body = MAPPER.readTree(response.body());
        Set<String> ids = new HashSet<>();
        body.get("entries").forEach(item -> ids.add(item.get("ticketId").asText()));
        assertEquals(
                Set.of("middle", "new"),
                ids,
                "sinceTick отдаёт только записи новее названного тика: наблюдатель продолжает журнал без "
                        + "повторов и потерь: " + response.body());
        assertEquals(30L, body.get("sinceTick").asLong(), "Ответ называет последний тик порции: с него читают дальше");
    }

    @Test
    void get_LogSinceTickNegative_Returns400NamingField() {
        RestResponse response = router.handle(new RestRequest(
                "GET", "/v1/board/log", Map.of("sinceTick", "-1"), Map.of(), "".getBytes(StandardCharsets.UTF_8)));

        assertEquals(400, response.status(), "Отрицательный sinceTick — ошибка запроса: " + response.body());
    }

    private static BoardLogEntry entry(String id, long tick) {
        return new BoardLogEntry(
                BoardLog.Event.DONE,
                tick,
                id,
                BoardCase.BUILD_PEN,
                1,
                "always",
                65,
                65,
                List.of(),
                null,
                "hash-1",
                tick);
    }
}
