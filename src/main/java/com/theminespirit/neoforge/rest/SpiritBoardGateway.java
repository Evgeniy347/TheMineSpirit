package com.theminespirit.neoforge.rest;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.RelocatePlan;
import com.theminespirit.core.board.SettlementStage;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.rest.ApiException;
import com.theminespirit.core.rest.BoardGateway;
import com.theminespirit.core.rest.BoardTask;
import com.theminespirit.core.rest.BoardTaskRequest;
import com.theminespirit.core.rest.BoardTasks;
import com.theminespirit.core.rest.ErrorCode;
import com.theminespirit.core.rest.RestTuning;
import com.theminespirit.core.rest.ServerThreadCall;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class SpiritBoardGateway implements BoardGateway {
    private static final String ID_PREFIX = "board-";

    private final MinecraftServer server;
    private final ServerThreadCall serverCall;

    public SpiritBoardGateway(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server не может быть null: доска привязана к серверу");
        this.serverCall = new ServerThreadCall(server);
    }

    public void stop() {
        serverCall.stop();
    }

    @Override
    public List<BoardTask> tasks() {
        return serverCall.call(this::readTasks, RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public BoardTask submit(BoardTaskRequest request) {
        return serverCall.call(() -> createTask(request), RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public BoardTask cancel(String ticketId) {
        return serverCall.call(() -> cancelTask(ticketId), RestTuning.SERVER_CALL_TIMEOUT);
    }

    @Override
    public List<BoardLogEntry> log(int limit) {
        return serverCall.call(
                () -> BoardRegistry.get(server.overworld()).journal().last(limit), RestTuning.SERVER_CALL_TIMEOUT);
    }

    private List<BoardTask> readTasks() {
        BoardService service = BoardRegistry.get(server.overworld());
        Board board = service.board();
        StockView stock = service.stock();
        List<BoardTask> tasks = new ArrayList<>();
        for (BoardTicket ticket : board.all()) {
            tasks.add(BoardTasks.of(board, ticket, stock));
        }
        return List.copyOf(tasks);
    }

    private BoardTask createTask(BoardTaskRequest request) {
        ServerLevel level = server.overworld();
        BoardService service = BoardRegistry.get(level);
        Board board = service.board();
        String id = ID_PREFIX + request.requestId();
        BoardTicket ticket = new BoardTicket(
                id,
                request.boardCase(),
                request.assignee(),
                BoardRules.priorityOf(request.boardCase()),
                level.getGameTime(),
                request.level(),
                request.cause(),
                request.dependsOn(),
                request.requiredItems());
        BoardOutcome outcome;
        if (request.boardCase() == BoardCase.RELOCATE_BUILDING) {
            outcome = service.submit(ticket, relocationTarget(request));
        } else {
            outcome = service.submit(ticket);
        }
        if (outcome == BoardOutcome.BACKOFF) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    "Задание '" + id + "' не принято доской: " + outcome + " — задание с этим request_id "
                            + "недавно провалено, повторите с новым request_id",
                    Map.of("request_id", request.requestId().toString(), "outcome", outcome.name()));
        }
        if (outcome == BoardOutcome.STAGE_LOCKED) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    "Задание '" + id + "' не принято доской: уровень " + request.level() + " постройки "
                            + request.boardCase() + " выше этапа поселения " + SettlementStage.of(board.ledger())
                            + " — сначала достройте этап: " + SettlementStage.missing(board.ledger()),
                    Map.of("request_id", request.requestId().toString(), "outcome", outcome.name()));
        }
        BoardTicket stored = board.find(id)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INTERNAL,
                        "Доска приняла задание '" + id + "', но не находит его в очереди: состояние доски "
                                + "испорчено, перезапустите сервер",
                        Map.of("request_id", request.requestId().toString())));
        return BoardTasks.of(board, stored, service.stock());
    }

    private static TicketTarget relocationTarget(BoardTaskRequest request) {
        BoardCase building = request.building()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INVALID_REQUEST,
                        "Перенос без 'building': неизвестно, что переносить",
                        Map.of("field", "building")));
        return TicketTarget.at(request.to().orElseThrow())
                .withRelocate(request.from().orElseThrow(), request.to().orElseThrow())
                .withScheme(RelocatePlan.schemeFor(building));
    }

    private BoardTask cancelTask(String ticketId) {
        ServerLevel level = server.overworld();
        BoardService service = BoardRegistry.get(level);
        Board board = service.board();
        BoardTicket ticket = board.find(ticketId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND,
                        "Задание доски '" + ticketId + "' не найдено: список заданий — GET /v1/board",
                        Map.of("id", ticketId)));
        service.cancel(level, ticketId);
        BoardStatus status = board.statusOf(ticketId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND,
                        "Задание доски '" + ticketId + "' исчезло после отмены: список заданий — GET /v1/board",
                        Map.of("id", ticketId)));
        return BoardTasks.of(board, ticket, status, service.stock());
    }
}
