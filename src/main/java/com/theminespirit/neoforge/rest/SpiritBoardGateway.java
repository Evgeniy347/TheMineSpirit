package com.theminespirit.neoforge.rest;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.rest.ApiException;
import com.theminespirit.core.rest.BoardGateway;
import com.theminespirit.core.rest.BoardTask;
import com.theminespirit.core.rest.BoardTaskRequest;
import com.theminespirit.core.rest.ErrorCode;
import com.theminespirit.core.rest.RestTuning;
import com.theminespirit.core.rest.ServerThreadCall;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Доска уровня для REST: наблюдение очереди поселения, постановка задания стратегом и отмена.
 * Мир читается только в серверном потоке через {@link ServerThreadCall}; id задания доска
 * назначает от {@code request_id} — повтор запроса с тем же UUID возвращает то же задание,
 * а не плодит второе.
 */
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

    private List<BoardTask> readTasks() {
        BoardService service = BoardRegistry.get(server.overworld());
        Board board = service.board();
        StockView stock = service.stock();
        List<BoardTask> tasks = new ArrayList<>();
        for (BoardTicket ticket : board.all()) {
            tasks.add(toTask(board, ticket, stock));
        }
        return List.copyOf(tasks);
    }

    private BoardTask createTask(BoardTaskRequest request) {
        ServerLevel level = server.overworld();
        BoardService service = BoardRegistry.get(level);
        Board board = service.board();
        String id = ID_PREFIX + request.requestId();
        BoardTicket ticket = new BoardTicket(id, request.boardCase(), request.assignee(),
                BoardRules.priorityOf(request.boardCase()), level.getGameTime(), request.ttlTicks(),
                request.dependsOn(), request.requiredItems());
        BoardOutcome outcome = service.submit(ticket);
        if (outcome == BoardOutcome.BACKOFF || outcome == BoardOutcome.EXPIRED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Задание '" + id + "' не принято доской: " + outcome + " — задание с этим request_id "
                            + "недавно провалено или просрочено, повторите с новым request_id",
                    Map.of("request_id", request.requestId().toString(), "outcome", outcome.name()));
        }
        BoardTicket stored = board.find(id).orElseThrow(() -> new ApiException(ErrorCode.INTERNAL,
                "Доска приняла задание '" + id + "', но не находит его в очереди: состояние доски "
                        + "испорчено, перезапустите сервер", Map.of("request_id", request.requestId().toString())));
        return toTask(board, stored, service.stock());
    }

    private BoardTask cancelTask(String ticketId) {
        ServerLevel level = server.overworld();
        BoardService service = BoardRegistry.get(level);
        Board board = service.board();
        BoardTicket ticket = board.find(ticketId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                "Задание доски '" + ticketId + "' не найдено: список заданий — GET /v1/board",
                Map.of("id", ticketId)));
        service.cancel(ticketId);
        BoardStatus status = board.statusOf(ticketId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                "Задание доски '" + ticketId + "' исчезло после отмены: список заданий — GET /v1/board",
                Map.of("id", ticketId)));
        return toTask(board, ticket, status, service.stock());
    }

    private static BoardTask toTask(Board board, BoardTicket ticket, StockView stock) {
        BoardStatus status = board.statusOf(ticket.id()).orElse(BoardStatus.PENDING);
        return toTask(board, ticket, status, stock);
    }

    private static BoardTask toTask(Board board, BoardTicket ticket, BoardStatus status, StockView stock) {
        Map<String, Integer> missing = ResourceGate.missing(new StockNeed(ticket.requiredItems()), stock);
        Optional<UUID> holder = board.holderOf(ticket.id());
        BoardCase boardCase = ticket.boardCase();
        return new BoardTask(ticket.id(), boardCase.name(), ticket.priority(),
                holder.map(UUID::toString).orElse(null), status.name(), ticket.requiredItems(), missing,
                ticket.dependsOn(), ticket.createdAt(), ticket.ttlTicks());
    }
}
