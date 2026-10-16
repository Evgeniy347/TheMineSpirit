package com.theminespirit.core.rest;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockView;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Сборка наблюдаемого задания доски из билета, статуса, держателя и снимка запасов.
 * Одна на REST и команду {@code /spirit board}: и мост, и чат показывают одно и то же.
 */
public final class BoardTasks {
    private BoardTasks() {
    }

    public static BoardTask of(Board board, BoardTicket ticket, StockView stock) {
        BoardStatus status = board.statusOf(ticket.id()).orElse(BoardStatus.PENDING);
        return of(board, ticket, status, stock);
    }

    public static BoardTask of(Board board, BoardTicket ticket, BoardStatus status, StockView stock) {
        Map<String, Integer> missing = ResourceGate.missing(new StockNeed(ticket.requiredItems()), stock);
        Optional<UUID> holder = board.holderOf(ticket.id());
        return new BoardTask(ticket.id(), ticket.boardCase().name(), ticket.priority(),
                holder.map(UUID::toString).orElse(null), status.name(), ticket.requiredItems(), missing,
                ticket.dependsOn(), ticket.createdAt(), ticket.level(), ticket.cause());
    }
}
