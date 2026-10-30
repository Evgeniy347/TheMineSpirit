package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.List;

public final class BoardNoticeText {
    private BoardNoticeText() {}

    public static String line(Board board, BoardLogEntry entry, StockView stock, long now) {
        BoardTicket ticket = board.find(entry.ticketId()).orElse(null);
        String waitReason = ticket == null
                ? null
                : BoardWhy.waitReason(board, ticket, stock, now).orElse(null);
        return StatusNote.line(
                entry.event(),
                entry.ticketId(),
                entry.boardCase(),
                board.ticketFailures(entry.ticketId()),
                waitReason,
                null,
                details(board, entry));
    }

    private static String details(Board board, BoardLogEntry entry) {
        List<String> parts = new ArrayList<>();
        if (entry.event() == BoardLog.Event.BLOCKED) {
            parts.add("причина=" + String.join(", ", entry.modifiers()));
            parts.add("история=" + String.join("; ", board.failureHistory(entry.ticketId())));
        } else if (entry.event() == BoardLog.Event.UNBLOCKED) {
            parts.add("изменилось: " + String.join(", ", entry.modifiers()));
            parts.add("старая причина: " + String.join("; ", board.failureHistory(entry.ticketId())));
        }
        return String.join(" ", parts);
    }
}
