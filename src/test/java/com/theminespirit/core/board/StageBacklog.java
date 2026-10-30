package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class StageBacklog {
    private final List<BoardTicket> tickets = new ArrayList<>();
    private long holdTicks;

    void add(BoardTicket ticket) {
        tickets.add(ticket);
    }

    void hold(long ticks) {
        holdTicks = Math.max(0, ticks);
    }

    long holdTicks() {
        return holdTicks;
    }

    int openCases(Board board) {
        return DispatchRound.stageCasesOpen(board);
    }

    long longestWait(Board board, long now) {
        long longest = 0;
        for (BoardTicket ticket : board.all()) {
            if (!StageTicket.stage(ticket.id()) || board.statusOf(ticket.id()).orElse(null) != BoardStatus.PENDING) {
                continue;
            }
            longest = Math.max(longest, now - ticket.createdAt());
        }
        return longest;
    }

    void open(Board board, long now, int spirits) {
        for (Iterator<BoardTicket> iterator = tickets.iterator(); iterator.hasNext(); ) {
            BoardTicket ticket = iterator.next();
            if (!board.dependenciesMet(ticket)) {
                continue;
            }
            if (DispatchRound.stageCasesOpen(board) >= BoardTuning.stageCaseLimit(spirits)) {
                return;
            }
            BoardOutcome outcome = board.submit(new BoardTicket(
                    ticket.id(),
                    ticket.boardCase(),
                    ticket.assignee(),
                    ticket.priority(),
                    now,
                    ticket.level(),
                    ticket.cause(),
                    ticket.dependsOn(),
                    ticket.requiredItems()));
            if (outcome == BoardOutcome.ACCEPTED || outcome == BoardOutcome.DUPLICATE) {
                iterator.remove();
            }
        }
    }
}
