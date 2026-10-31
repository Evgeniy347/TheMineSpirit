package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardJournal;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.LiveBoardFixture;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.task.TaskRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskBoardConsistencyTests {
    private static final String LIVE_BOARD = "/live/observe11-late-board.json";
    private static final Path LIVE_JOURNAL = Path.of("src/test/resources/live/observe11-journal-tail.jsonl");
    private static final String CAULDRON = "move:-92,67,-113:minecraft:cauldron";
    private static final String ORCHARD = "first:build_orchard";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void get_TicketPrunedFromLiveBoard_ReturnsJournalCardInsteadOf404() throws Exception {
        Board board = liveBoard();
        assertFalse(
                board.find(CAULDRON).isPresent(),
                "Живая доска observe11 уже выбросила проваленный перенос: именно в этот момент /v1/tasks отвечал 404");

        RestResponse response = task(board, new BoardJournal(LIVE_JOURNAL), CAULDRON);

        assertEquals(
                200,
                response.status(),
                "/v1/tasks обязан отдать тикет, который ушёл с доски в журнал: на живой доске он был ASSIGNED, "
                        + "а ответ 404 «сервер перезапущен» врал: " + response.body());
        JsonNode task = MAPPER.readTree(response.body());
        assertEquals(CAULDRON, task.get("id").asText(), "Ответ обязан назвать задание из журнала");
        assertEquals("SUPPLY_SITE", task.get("case").asText(), "Ответ обязан назвать кейс из журнала");
        assertEquals("FAILED", task.get("status").asText(), "Последний статус переноса в журнале живой доски — FAILED");
    }

    @Test
    void get_TicketOnLiveBoard_MatchesBoardListing() throws Exception {
        Board board = liveBoard();
        assertTrue(
                board.find(ORCHARD).isPresent(),
                "Живая доска observe11 держит сад тикетом — без него проверка согласованности слепа");

        RestResponse listing = send(board, new BoardJournal(LIVE_JOURNAL), "GET", "/v1/board");
        JsonNode tasks = MAPPER.readTree(listing.body()).get("tasks");
        boolean listed = StreamSupport.stream(tasks.spliterator(), false)
                .anyMatch(task -> ORCHARD.equals(task.get("id").asText()));
        assertTrue(listed, "/v1/board обязан перечислять сад: иначе сверять нечего");

        RestResponse response = task(board, new BoardJournal(LIVE_JOURNAL), ORCHARD);

        assertEquals(
                200,
                response.status(),
                "Тикет, который /v1/board показывает, обязан быть в /v1/tasks: " + response.body());
        assertEquals(
                "FAILED",
                MAPPER.readTree(response.body()).get("status").asText(),
                "Статус в /v1/tasks обязан совпадать со статусом на /v1/board");
    }

    @Test
    void get_TicketInUnflushedJournalQueue_ReturnsJournalCard(@TempDir Path dir) throws Exception {
        BoardJournal journal = new BoardJournal(dir.resolve("board-log.jsonl"));
        journal.enqueue(new BoardLogEntry(
                BoardLog.Event.ASSIGNED,
                900,
                CAULDRON,
                BoardCase.SUPPLY_SITE,
                1,
                BoardTicket.ALWAYS_CAUSE,
                40,
                40,
                List.of(),
                "c48663b4-da59-43d9-9804-81a680580300",
                "e2dbe19f99ee",
                900));

        RestResponse response = task(new Board(() -> 0), journal, CAULDRON);

        assertEquals(
                200,
                response.status(),
                "Тикет, снятый с доски в том же тике, ещё лежит в очереди журнала: /v1/tasks не имеет права "
                        + "отвечать 404, пока запись не сброшена на диск — это и есть гонка переоткрытия: "
                        + response.body());
        assertEquals(
                "ASSIGNED",
                MAPPER.readTree(response.body()).get("status").asText(),
                "Ответ обязан назвать статус из несохранённой записи журнала");
    }

    private static RestResponse task(Board board, BoardJournal journal, String id) {
        return send(board, journal, "GET", "/v1/tasks/" + id);
    }

    private static RestResponse send(Board board, BoardJournal journal, String method, String path) {
        PlanningService planning = new PlanningService(CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        BoardGateway gateway = new JournalBoardGateway(board, journal);
        TaskRoutes taskRoutes = new TaskRoutes(
                new TaskRegistry(),
                new FakeTaskGateway().planning(planning),
                planning,
                new IdempotencyCache(),
                new SseSlots(),
                gateway);
        BoardRoutes boardRoutes = new BoardRoutes(gateway, new IdempotencyCache());
        List<Route> routes = new ArrayList<>(taskRoutes.routes());
        routes.addAll(boardRoutes.routes());
        return Router.of(routes).handle(new RestRequest(method, path, Map.of(), Map.of(), new byte[0]));
    }

    private static Board liveBoard() throws IOException {
        return LiveBoardFixture.load(LIVE_BOARD);
    }

    private static final class JournalBoardGateway implements BoardGateway {
        private final Board board;
        private final BoardJournal journal;

        private JournalBoardGateway(Board board, BoardJournal journal) {
            this.board = board;
            this.journal = journal;
        }

        @Override
        public List<BoardTask> tasks() {
            List<BoardTask> tasks = new ArrayList<>();
            for (BoardTicket ticket : board.all()) {
                tasks.add(BoardTasks.of(board, ticket, new StockView(Map.of())));
            }
            return List.copyOf(tasks);
        }

        @Override
        public Optional<BoardTask> task(String ticketId) {
            return board.find(ticketId).map(ticket -> BoardTasks.of(board, ticket, new StockView(Map.of())));
        }

        @Override
        public Optional<BoardTask> history(String ticketId) {
            return journal.findStatus(ticketId, BoardJournal.FIND_LINES).map(BoardTasks::fromJournal);
        }

        @Override
        public BoardTask submit(BoardTaskRequest request) {
            throw new AssertionError("Тест только читает задания: постановка не нужна");
        }

        @Override
        public BoardTask cancel(String ticketId) {
            throw new AssertionError("Тест только читает задания: отмена не нужна");
        }

        @Override
        public List<BoardLogEntry> log(int limit) {
            return List.of();
        }
    }
}
