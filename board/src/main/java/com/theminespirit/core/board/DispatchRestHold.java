package com.theminespirit.core.board;

import java.util.UUID;

public final class DispatchRestHold {
    private DispatchRestHold() {}

    public static boolean held(Board board, UUID spirit) {
        for (BoardTicket ticket : board.assignedTo(spirit)) {
            if (RestTickets.is(ticket.id())) {
                return true;
            }
        }
        return false;
    }
}
