package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.SupplyGate;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class SupplyTickets {
    private SupplyTickets() {}

    static void issue(
            BoardService service,
            Map<String, TicketTarget> targets,
            BlockCoord home,
            SupplyGate.Request request,
            BoardCase boardCase,
            String prefix,
            long now) {
        String id = prefix + request.item();
        TicketTarget target = targets.values().stream()
                .filter(candidate -> request.item().equals(candidate.supplyItem()))
                .findFirst()
                .orElse(TicketTarget.at(home));
        service.submit(
                new BoardTicket(
                        id,
                        boardCase,
                        Optional.empty(),
                        BoardRules.priorityOf(boardCase),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                target.withSupply(request.item(), request.count()));
    }
}
