package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GraphCollect {
    private GraphCollect() {}

    static boolean post(GraphPost post, GraphSurroundings.Around around, GraphTickets.Ticket ticket) {
        String item = GraphNames.graphItem(ticket.task().makes());
        Optional<BlockCoord> at = around.drop(item);
        if (at.isEmpty()) {
            return true;
        }
        post.submit(
                post.ticket(
                        ticket.id(),
                        BoardCase.COLLECT_DROPS,
                        Optional.empty(),
                        ticket.task().priority(),
                        ticket.id(),
                        Map.of()),
                TicketTarget.at(at.orElseThrow()).withDrops(List.of(item)));
        return true;
    }
}
