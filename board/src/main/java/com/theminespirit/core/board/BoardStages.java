package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.List;

public final class BoardStages {
    private BoardStages() {}

    public static List<String> held(Board board) {
        int stage = board.ledger().stage();
        List<String> held = new ArrayList<>();
        for (BoardTicket ticket : board.all()) {
            BoardStatus status = board.statusOf(ticket.id()).orElse(null);
            if (status == null
                    || status == BoardStatus.DONE
                    || status == BoardStatus.CANCELLED
                    || SettlementStage.allows(ticket.boardCase(), ticket.level(), stage)) {
                continue;
            }
            held.add(ticket.id());
        }
        return List.copyOf(held);
    }
}
