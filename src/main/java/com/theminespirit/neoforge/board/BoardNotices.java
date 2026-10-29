package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardWhy;
import com.theminespirit.core.board.PendingStall;
import com.theminespirit.core.board.StatusNote;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;

final class BoardNotices {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final PendingStall pending = new PendingStall();

    void blocks(Board board, JournalService journal) {
        for (BoardLogEntry entry : board.drainBlocks()) {
            journal.recordEntry(entry);
            LOGGER.info(StatusNote.line(
                    entry.event(),
                    entry.ticketId(),
                    entry.boardCase(),
                    board.ticketFailures(entry.ticketId()),
                    board.waitNote(entry.ticketId()).orElse(null),
                    null,
                    details(board, entry)));
        }
    }

    void assigned(Board board, BoardTicket ticket, TicketTarget target, String chosen) {
        String details = "цель=" + target.target() + " предмет=" + text(target.supplyItem()) + " количество="
                + target.count() + " попыток=" + board.ticketFailures(ticket.id());
        LOGGER.info(StatusNote.line(
                BoardLog.Event.ASSIGNED,
                ticket.id(),
                ticket.boardCase(),
                board.ticketFailures(ticket.id()),
                board.waitNote(ticket.id()).orElse(null),
                chosen,
                details));
    }

    private static String details(Board board, BoardLogEntry entry) {
        List<String> parts = new ArrayList<>();
        if (entry.event() == BoardLog.Event.BLOCKED) {
            parts.add("причина=" + String.join(", ", entry.modifiers()));
            List<String> history = board.failureHistory(entry.ticketId());
            parts.add("история=" + String.join("; ", history));
        } else if (entry.event() == BoardLog.Event.UNBLOCKED) {
            parts.add("изменилось: " + String.join(", ", entry.modifiers()));
        }
        return String.join(" ", parts);
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    void watchPending(Board board, StockView stock, long now) {
        Set<String> waiting = new HashSet<>();
        for (BoardTicket ticket : board.all()) {
            if (board.statusOf(ticket.id()).orElse(null) != BoardStatus.PENDING) {
                continue;
            }
            waiting.add(ticket.id());
            String reason = BoardWhy.waitReason(board, ticket, stock, now).orElse(null);
            pending.observe(ticket.id(), reason, now).ifPresent(LOGGER::warn);
        }
        pending.retain(waiting);
    }
}
