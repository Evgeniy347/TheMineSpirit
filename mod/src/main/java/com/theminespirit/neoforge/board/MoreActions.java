package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class MoreActions {
    private MoreActions() {}

    static boolean script(
            GraphPost post, GraphTickets.Ticket ticket, BoardCase boardCase, BlockCoord at, @Nullable TaskNode plan) {
        TicketTarget target = TicketTarget.at(at);
        if (plan != null) {
            target = target.withPlan(plan);
        }
        return submit(post, ticket, boardCase, target);
    }

    static boolean submit(GraphPost post, GraphTickets.Ticket ticket, BoardCase boardCase, TicketTarget target) {
        return submit(post, ticket, ticket.id(), boardCase, target, Map.of());
    }

    static boolean submit(
            GraphPost post,
            GraphTickets.Ticket ticket,
            BoardCase boardCase,
            TicketTarget target,
            Map<String, Integer> required) {
        return submit(post, ticket, ticket.id(), boardCase, target, required);
    }

    static boolean submit(
            GraphPost post,
            GraphTickets.Ticket ticket,
            String id,
            BoardCase boardCase,
            TicketTarget target,
            Map<String, Integer> required) {
        FarmPost.submit(post, id, boardCase, ticket.task().priority(), target, required);
        return true;
    }
}
