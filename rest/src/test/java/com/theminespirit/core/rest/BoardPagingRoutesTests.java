package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoardPagingRoutesTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FakeBoardGateway gateway;
    private Router router;

    @BeforeEach
    void setUp() {
        gateway = new FakeBoardGateway();
        router = Router.of(new BoardRoutes(gateway, new IdempotencyCache()).routes());
    }

    private RestResponse send(String method, String path, String body) {
        return router.handle(new RestRequest(method, path, Map.of(), Map.of(), body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void get_DefaultPaging_ReturnsPortionWithTotalAndNextCursor() throws Exception {
        for (int i = 0; i < 60; i++) {
            gateway.with(task("board-" + i, "PENDING", i, null));
        }

        RestResponse response = send("GET", "/v1/board", "");

        JsonNode body = MAPPER.readTree(response.body());
        assertEquals(
                BoardListQuery.DEFAULT_LIMIT,
                body.get("tasks").size(),
                "GET без параметров обязан отдавать разумную порцию, а не всю доску: " + response.body()
                        + " Иначе тест get_DefaultPaging_ReturnsPortionWithTotalAndNextCursor перестанет ловить эту поломку. При смене get_DefaultPaging_ReturnsPortionWithTotalAndNextCursor проверь это утверждение.");
        assertEquals(
                60,
                body.get("total").asInt(),
                "GET обязан сообщать общее число подходящих заданий"
                        + " Иначе тест get_DefaultPaging_ReturnsPortionWithTotalAndNextCursor перестанет ловить эту поломку. При смене get_DefaultPaging_ReturnsPortionWithTotalAndNextCursor проверь это утверждение.");
        assertTrue(
                body.hasNonNull("nextCursor"),
                "Пока есть хвост, ответ обязан давать курсор следующей порции"
                        + " Иначе тест get_DefaultPaging_ReturnsPortionWithTotalAndNextCursor перестанет ловить эту поломку. При смене get_DefaultPaging_ReturnsPortionWithTotalAndNextCursor проверь это утверждение.");
        assertEquals(
                60,
                body.get("groups").get("AVAILABLE").asInt(),
                "Счётчики колонок обязаны считать всю подходящую доску, а не только порцию: " + response.body()
                        + " Иначе тест get_DefaultPaging_ReturnsPortionWithTotalAndNextCursor перестанет ловить эту поломку. При смене get_DefaultPaging_ReturnsPortionWithTotalAndNextCursor проверь это утверждение.");
    }

    @Test
    void get_StatusFilter_ReturnsOnlyMatchingTasksWithTotal() throws Exception {
        gateway.with(task("done-a", "DONE", 1, null));
        gateway.with(task("done-b", "DONE", 2, null));
        gateway.with(task("free-a", "PENDING", 3, null));

        RestResponse response =
                router.handle(new RestRequest("GET", "/v1/board", Map.of("status", "DONE"), Map.of(), new byte[0]));

        JsonNode body = MAPPER.readTree(response.body());
        assertEquals(
                2,
                body.get("tasks").size(),
                "Фильтр status обязан оставить только DONE: " + response.body()
                        + " Иначе тест get_StatusFilter_ReturnsOnlyMatchingTasksWithTotal перестанет ловить эту поломку. При смене get_StatusFilter_ReturnsOnlyMatchingTasksWithTotal проверь это утверждение.");
        assertEquals(
                2,
                body.get("total").asInt(),
                "total обязан считать отфильтрованные задания"
                        + " Иначе тест get_StatusFilter_ReturnsOnlyMatchingTasksWithTotal перестанет ловить эту поломку. При смене get_StatusFilter_ReturnsOnlyMatchingTasksWithTotal проверь это утверждение.");
        assertEquals(
                Set.of("done-a", "done-b"),
                ids(body),
                "Фильтр status обязан отдавать именно задания этого статуса: " + response.body()
                        + " Иначе тест get_StatusFilter_ReturnsOnlyMatchingTasksWithTotal перестанет ловить эту поломку. При смене get_StatusFilter_ReturnsOnlyMatchingTasksWithTotal проверь это утверждение.");
    }

    @Test
    void get_SinceTick_ReturnsOnlyNewerTasksAndEchoesTick() throws Exception {
        for (int i = 0; i < 5; i++) {
            gateway.with(task("board-" + i, "PENDING", i, null));
        }

        RestResponse response =
                router.handle(new RestRequest("GET", "/v1/board", Map.of("sinceTick", "2"), Map.of(), new byte[0]));

        JsonNode body = MAPPER.readTree(response.body());
        assertEquals(
                Set.of("board-2", "board-3", "board-4"),
                ids(body),
                "sinceTick на доске отдаёт задания, поставленные на этом тике и позже: наблюдатель опрашивает "
                        + "только новое"
                        + " Иначе тест get_SinceTick_ReturnsOnlyNewerTasksAndEchoesTick перестанет ловить эту поломку. При смене get_SinceTick_ReturnsOnlyNewerTasksAndEchoesTick проверь это утверждение.");
        assertEquals(
                2L,
                body.get("sinceTick").asLong(),
                "Ответ эхает sinceTick: по нему клиент продолжает опрос"
                        + " Иначе тест get_SinceTick_ReturnsOnlyNewerTasksAndEchoesTick перестанет ловить эту поломку. При смене get_SinceTick_ReturnsOnlyNewerTasksAndEchoesTick проверь это утверждение.");
    }

    @Test
    void get_OffsetAndLimit_ReturnsSecondPortion() throws Exception {
        for (int i = 0; i < 5; i++) {
            gateway.with(task("board-" + i, "PENDING", i, null));
        }

        RestResponse response = router.handle(
                new RestRequest("GET", "/v1/board", Map.of("limit", "2", "offset", "2"), Map.of(), new byte[0]));

        JsonNode body = MAPPER.readTree(response.body());
        assertEquals(
                List.of("board-2", "board-3"),
                sortedIds(body),
                "offset и limit обязаны нарезать ленту по порядку"
                        + " Иначе тест get_OffsetAndLimit_ReturnsSecondPortion перестанет ловить эту поломку. При смене get_OffsetAndLimit_ReturnsSecondPortion проверь это утверждение.");
        assertEquals(
                5,
                body.get("total").asInt(),
                "total обязан считать все подходящие задания, а не размер порции"
                        + " Иначе тест get_OffsetAndLimit_ReturnsSecondPortion перестанет ловить эту поломку. При смене get_OffsetAndLimit_ReturnsSecondPortion проверь это утверждение.");
    }

    @Test
    void get_CursorSecondPage_ContinuesWithoutDuplicates() throws Exception {
        for (int i = 0; i < 5; i++) {
            gateway.with(task("board-" + i, "PENDING", i, null));
        }

        RestResponse first =
                router.handle(new RestRequest("GET", "/v1/board", Map.of("limit", "2"), Map.of(), new byte[0]));
        String cursor = MAPPER.readTree(first.body()).get("nextCursor").asText();
        RestResponse second = router.handle(
                new RestRequest("GET", "/v1/board", Map.of("limit", "2", "cursor", cursor), Map.of(), new byte[0]));

        assertEquals(
                List.of("board-2", "board-3"),
                sortedIds(MAPPER.readTree(second.body())),
                "Курсор обязан продолжить ленту со следующей порции без повторов: " + second.body()
                        + " Иначе тест get_CursorSecondPage_ContinuesWithoutDuplicates перестанет ловить эту поломку. При смене get_CursorSecondPage_ContinuesWithoutDuplicates проверь это утверждение.");
    }

    @Test
    void get_GroupFilter_ReturnsOnlyRequestedColumn() throws Exception {
        gateway.with(task("free-a", "PENDING", 1, null));
        gateway.with(task("assigned-a", "ASSIGNED", 2, null));

        RestResponse response =
                router.handle(new RestRequest("GET", "/v1/board", Map.of("group", "ACTIVE"), Map.of(), new byte[0]));

        assertEquals(
                Set.of("assigned-a"),
                ids(MAPPER.readTree(response.body())),
                "Фильтр group обязан отдавать только плитки запрошенной колонки: " + response.body()
                        + " Иначе тест get_GroupFilter_ReturnsOnlyRequestedColumn перестанет ловить эту поломку. При смене get_GroupFilter_ReturnsOnlyRequestedColumn проверь это утверждение.");
    }

    @Test
    void get_UnknownQueryParameter_Returns400WithName() {
        RestResponse response =
                router.handle(new RestRequest("GET", "/v1/board", Map.of("filter", "crater"), Map.of(), new byte[0]));

        assertEquals(
                400,
                response.status(),
                "Неизвестный параметр доски обязан давать 400: " + response.body()
                        + " Иначе тест get_UnknownQueryParameter_Returns400WithName перестанет ловить эту поломку. При смене get_UnknownQueryParameter_Returns400WithName проверь это утверждение.");
        assertTrue(
                response.body().contains("filter"),
                "400 обязан называть параметр: " + response.body()
                        + " Иначе тест get_UnknownQueryParameter_Returns400WithName перестанет ловить эту поломку. При смене get_UnknownQueryParameter_Returns400WithName проверь это утверждение.");
    }

    private static BoardTask task(String id, String status, long createdAt, String assignee) {
        return new BoardTask(
                id, "BUILD_FENCE", 50, assignee, status, Map.of(), Map.of(), List.of(), createdAt, 1, "always");
    }

    private static Set<String> ids(JsonNode body) {
        Set<String> found = new HashSet<>();
        for (JsonNode task : body.get("tasks")) {
            found.add(task.get("id").asText());
        }
        return found;
    }

    private static List<String> sortedIds(JsonNode body) {
        List<String> found = new ArrayList<>();
        for (JsonNode task : body.get("tasks")) {
            found.add(task.get("id").asText());
        }
        return found.stream().sorted().toList();
    }
}
