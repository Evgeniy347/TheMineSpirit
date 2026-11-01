package com.theminespirit.core.board;

import java.util.Objects;
import java.util.UUID;

public final class EvergreenRules {
    private EvergreenRules() {}

    public static boolean issuedTo(BoardTicket ticket, UUID spirit, EatNeed eat, SpiritView view) {
        Objects.requireNonNull(ticket, "Задание выдачи не может быть null: без него непонятно, что выдаём");
        Objects.requireNonNull(spirit, "Дух выдачи не может быть null: выдача идёт конкретному духу");
        if (ticket.boardCase() != BoardCase.TRANSFER_ITEM
                && ticket.assignee().isPresent()
                && !ticket.assignee().orElseThrow().equals(spirit)) {
            return false;
        }
        return Applicability.applicable(ticket.boardCase(), eat, view);
    }
}
