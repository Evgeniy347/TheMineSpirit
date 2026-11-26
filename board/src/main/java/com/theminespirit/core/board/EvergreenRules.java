package com.theminespirit.core.board;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphTickets;
import java.util.Objects;
import java.util.UUID;

public final class EvergreenRules {
    private EvergreenRules() {}

    public static boolean issuedTo(BoardTicket ticket, UUID spirit, EatNeed eat, SpiritView view) {
        Objects.requireNonNull(ticket, "Задание выдачи не может быть null: без него непонятно, что выдаём");
        Objects.requireNonNull(spirit, "Дух выдачи не может быть null: выдача идёт конкретному духу");
        boolean graphOwned =
                ticket.id().startsWith(GraphTickets.PREFIX) || ticket.cause().startsWith(GraphTickets.PREFIX);
        if (ticket.boardCase() != BoardCase.TRANSFER_ITEM
                && !graphOwned
                && ticket.assignee().isPresent()
                && !ticket.assignee().orElseThrow().equals(spirit)) {
            return false;
        }
        return Applicability.applicable(ticket.boardCase(), eat, view);
    }
}
