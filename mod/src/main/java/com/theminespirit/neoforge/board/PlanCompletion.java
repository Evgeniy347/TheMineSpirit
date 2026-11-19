package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.api.BoardCase;
import org.jspecify.annotations.Nullable;

final class PlanCompletion {
    private PlanCompletion() {}

    static void complete(Board board, BoardTicket ticket, @Nullable TicketTarget target) {
        if (!board.complete(ticket.id())) {
            return;
        }
        if (ticket.boardCase() != BoardCase.BUILD_PLACED || target == null || target.building() == null) {
            return;
        }
        board.ledger().notePlan(target.building(), ticket.level());
    }
}
