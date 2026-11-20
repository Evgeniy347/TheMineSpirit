package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;

final class OrchardBuildPost {
    private OrchardBuildPost() {}

    static boolean post(GraphPost post, GraphTickets.Ticket ticket, GraphPlan.Task task) {
        boolean extra = ExtraOrchardPost.handles(ticket.node()) && ExtraOrchardPost.post(post, ticket, task);
        boolean main = StagePlacePost.handles(ticket.node()) && StagePlacePost.post(post, ticket);
        return extra || main;
    }
}
