package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.task.TaskRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskBoardFallbackTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BOT = "6f1c2d3e-0000-4000-8000-000000000001";

    private static BoardTask boardTask() {
        return new BoardTask(
                "graph:chop_jungle_log",
                "GATHER_MATERIALS",
                50,
                BOT,
                "ASSIGNED",
                Map.of("jungle_log", 8),
                Map.of("jungle_log", 3),
                List.of(),
                19620310L,
                1,
                "graph",
                50,
                List.of(),
                false,
                19620400L,
                0L,
                0L,
                "spirit-3 — занят: chop_tree");
    }

    private static RestResponse send(FakeBoardGateway board, String method, String path) {
        PlanningService planning = new PlanningService(CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        TaskRoutes routes = new TaskRoutes(
                new TaskRegistry(),
                new FakeTaskGateway().planning(planning),
                planning,
                new IdempotencyCache(),
                new SseSlots(),
                board);
        return Router.of(routes.routes()).handle(new RestRequest(method, path, Map.of(), Map.of(), new byte[0]));
    }

    @Test
    void get_BoardTicket_ReturnsBoardStatusWaitReasonAndAssignee() throws Exception {
        FakeBoardGateway board = new FakeBoardGateway().with(boardTask());

        RestResponse response = send(board, "GET", "/v1/tasks/graph:chop_jungle_log");

        assertEquals(200, response.status(), "Задание доски обязано отдаваться по /v1/tasks/{id}: " + response.body());
        JsonNode task = MAPPER.readTree(response.body());
        assertEquals("graph:chop_jungle_log", task.get("id").asText(), "Ответ обязан назвать задание доски");
        assertEquals("ASSIGNED", task.get("status").asText(), "Ответ обязан назвать статус задания доски");
        assertEquals(BOT, task.get("assignee").asText(), "Ответ обязан назвать держателя задания доски");
        assertEquals(
                "spirit-3 — занят: chop_tree",
                task.get("waitReason").asText(),
                "Ответ обязан отдать причину ожидания задания доски");
    }

    @Test
    void get_RestoredBoardTicket_ReturnedFromBoardRegistryNotSnapshot() throws Exception {
        FakeBoardGateway board = new FakeBoardGateway().withRestored(boardTask());

        RestResponse response = send(board, "GET", "/v1/tasks/graph:chop_jungle_log");

        assertEquals(
                200,
                response.status(),
                "Задание, восстановленное из сейва, живёт в реестре доски: 404 «сервер перезапущен» врёт, "
                        + "хотя бы его и не было в списке на момент снимка: " + response.body());
        JsonNode task = MAPPER.readTree(response.body());
        assertEquals("graph:chop_jungle_log", task.get("id").asText(), "Ответ обязан назвать задание доски");
        assertEquals(BOT, task.get("assignee").asText(), "Ответ обязан назвать держателя задания доски");
    }

    @Test
    void get_UnknownTicket_StillReportsLostTask() {
        RestResponse response = send(new FakeBoardGateway(), "GET", "/v1/tasks/нет-такого");

        assertEquals(404, response.status(), "Неизвестное задание обязано давать 404: " + response.body());
        assertTrue(
                response.body().contains("\"code\":\"not_found\""),
                "404 обязан нести код not_found: " + response.body());
    }
}
