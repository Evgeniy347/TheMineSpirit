package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.board.BoardJournal;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.task.TaskRegistry;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TaskJournalFallbackTests {
    private static final Path JOURNAL = Path.of("src/test/resources/live/observe10-journal-chests1.jsonl");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void get_PrunedBoardTicket_ReturnsLastJournalCardInsteadOfLostTask() throws Exception {
        FakeHistoryBoard board = new FakeHistoryBoard(JOURNAL);

        RestResponse response = send(board, "GET", "/v1/tasks/chests:1");

        assertEquals(
                200,
                response.status(),
                "Тикет доски вне REST-реестра обязан находиться по журналу доски: chests:1 провалился и был "
                        + "убран с живой доски, но остался в источнике, а 404 «сервер перезапущен» врёт: "
                        + response.body());
        JsonNode task = MAPPER.readTree(response.body());
        assertEquals("chests:1", task.get("id").asText(), "Ответ обязан назвать задание из журнала");
        assertEquals("STOCK_CHESTS", task.get("case").asText(), "Ответ обязан назвать кейс из журнала");
        assertEquals("FAILED", task.get("status").asText(), "Последний статус chests:1 в журнале — FAILED");
        assertEquals(
                "c48663b4-da59-43d9-9804-81a680580300",
                task.get("assignee").asText(),
                "Ответ обязан назвать духа, провалившего chests:1 по журналу");
        assertEquals(22543773L, task.get("finishedAt").asLong(), "Ответ обязан назвать тик провала из журнала");
        assertTrue(!task.get("done").asBoolean(), "Проваленное задание не считается выполненным");
    }

    @Test
    void get_UnknownTicket_StillReportsLostTask() {
        RestResponse response = send(new FakeHistoryBoard(JOURNAL), "GET", "/v1/tasks/нет-такого");

        assertEquals(404, response.status(), "Неизвестное задание обязано давать 404: " + response.body());
        assertTrue(
                response.body().contains("\"code\":\"not_found\""),
                "404 обязан нести код not_found: " + response.body());
    }

    private static RestResponse send(BoardGateway board, String method, String path) {
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

    private static final class FakeHistoryBoard implements BoardGateway {
        private final BoardJournal journal;

        private FakeHistoryBoard(Path path) {
            this.journal = new BoardJournal(path);
        }

        @Override
        public List<BoardTask> tasks() {
            return List.of();
        }

        @Override
        public Optional<BoardTask> history(String ticketId) {
            return journal.findStatus(ticketId, 1024).map(BoardTasks::fromJournal);
        }

        @Override
        public BoardTask submit(BoardTaskRequest request) {
            throw new AssertionError("Тест только читает задание: постановка не нужна");
        }

        @Override
        public BoardTask cancel(String ticketId) {
            throw new AssertionError("Тест только читает задание: отмена не нужна");
        }

        @Override
        public List<BoardLogEntry> log(int limit) {
            return List.of();
        }
    }
}
