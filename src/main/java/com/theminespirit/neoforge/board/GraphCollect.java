package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GraphCollect {
    private GraphCollect() {}

    static boolean post(BoardService service, GraphSurroundings.Around around, GraphTickets.Ticket ticket, long now) {
        String item = GraphNames.graphItem(ticket.task().makes());
        Optional<BlockCoord> at = around.drop(item);
        if (at.isEmpty()) {
            return true;
        }
        BoardTicket boardTicket = new BoardTicket(
                ticket.id(),
                BoardCase.COLLECT_DROPS,
                Optional.empty(),
                ticket.task().priority(),
                now,
                1,
                ticket.id(),
                List.of(),
                Map.of());
        BoardOutcome outcome =
                service.submit(boardTicket, TicketTarget.at(at.orElseThrow()).withDrops(List.of(item)));
        if (outcome == BoardOutcome.DUPLICATE) {
            service.board().reprioritize(ticket.id(), ticket.task().priority());
        }
        return true;
    }
}
