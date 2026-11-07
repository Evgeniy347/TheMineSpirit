package com.theminespirit.core.rest;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTiming;
import com.theminespirit.core.board.BoardWhy;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class BoardTasks {
    private BoardTasks() {}

    public static BoardTask fromJournal(BoardLogEntry entry) {
        Objects.requireNonNull(entry, "Запись журнала не может быть null: из неё строится карточка задания");
        BoardStatus status = statusOf(entry.event());
        boolean closed = status == BoardStatus.DONE || status == BoardStatus.FAILED || status == BoardStatus.CANCELLED;
        return new BoardTask(
                entry.ticketId(),
                entry.boardCase().name(),
                entry.basePriority(),
                entry.spirit(),
                status.name(),
                Map.of(),
                Map.of(),
                List.of(),
                entry.tick(),
                entry.level(),
                entry.cause(),
                entry.effectivePriority(),
                entry.modifiers(),
                status == BoardStatus.DONE,
                0L,
                closed ? entry.tick() : 0L,
                0L,
                null,
                null,
                0);
    }

    private static BoardStatus statusOf(BoardLog.Event event) {
        return switch (event) {
            case ASSIGNED -> BoardStatus.ASSIGNED;
            case DONE -> BoardStatus.DONE;
            case FAILED -> BoardStatus.FAILED;
            case BLOCKED -> BoardStatus.BLOCKED;
            case CANCELLED, DETACHED -> BoardStatus.CANCELLED;
            case POSTED, SUBMITTED, UNBLOCKED, REOPENED, NOTE -> BoardStatus.PENDING;
        };
    }

    public static BoardTask of(Board board, BoardTicket ticket, StockView stock) {
        BoardStatus status = board.statusOf(ticket.id()).orElse(BoardStatus.PENDING);
        return of(board, ticket, status, stock, BoardTiming.NONE);
    }

    public static BoardTask of(Board board, BoardTicket ticket, BoardStatus status, StockView stock) {
        return of(board, ticket, status, stock, BoardTiming.NONE);
    }

    public static BoardTask of(
            Board board, BoardTicket ticket, BoardStatus status, StockView stock, BoardTiming timing) {
        return of(board, ticket, status, stock, timing, null);
    }

    public static BoardTask of(
            Board board,
            BoardTicket ticket,
            BoardStatus status,
            StockView stock,
            BoardTiming timing,
            @Nullable TicketTarget target) {
        Map<String, Integer> missing = ResourceGate.missing(new StockNeed(ticket.requiredItems()), stock);
        Optional<UUID> holder = board.holderOf(ticket.id());
        boolean queued = status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED;
        boolean done = status == BoardStatus.DONE
                || (status != BoardStatus.BLOCKED && !queued && board.ledger().has(ticket.boardCase(), ticket.level()));
        BoardTiming attempt = BoardTiming.attempt(status, timing);
        return new BoardTask(
                ticket.id(),
                ticket.boardCase().name(),
                ticket.priority(),
                holder.map(UUID::toString).orElse(null),
                status.name(),
                ticket.requiredItems(),
                missing,
                ticket.dependsOn(),
                ticket.createdAt(),
                ticket.level(),
                ticket.cause(),
                ticket.priority(),
                ticket.modifiers(),
                done,
                attempt.startedAt(),
                attempt.finishedAt(),
                attempt.durationTicks(board.now()),
                BoardWhy.waitReason(board, ticket, stock, board.now()).orElse(null),
                target == null ? null : target.supplyItem(),
                target == null ? 0 : target.supplyCount(),
                ticket.executors().stream().map(UUID::toString).toList());
    }
}
