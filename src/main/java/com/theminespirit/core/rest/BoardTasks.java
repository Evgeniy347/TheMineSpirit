package com.theminespirit.core.rest;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTiming;
import com.theminespirit.core.board.BoardWhy;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class BoardTasks {
    private BoardTasks() {}

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
        BoardTiming attempt =
                BoardTiming.attempt(status, timing, BoardActivities.isActivity(ticket.boardCase()), ticket.createdAt());
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
                target == null ? 0 : target.supplyCount());
    }
}
