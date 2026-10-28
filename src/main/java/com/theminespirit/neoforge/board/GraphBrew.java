package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import java.util.Optional;

final class GraphBrew {
    private GraphBrew() {}

    static boolean post(GraphPost post, GraphTickets.Ticket ticket, GraphPlan.Task task) {
        String item = task.boardCase() == BoardCase.GRIND_BLAZE_POWDER ? "minecraft:blaze_powder" : task.makes();
        post.submit(
                post.ticket(
                        ticket.id(),
                        task.boardCase(),
                        Optional.empty(),
                        task.priority(),
                        ticket.id(),
                        post.items(task)),
                TicketTarget.at(post.craftPoint()).withCraft(item, ticket.count()));
        return true;
    }
}
