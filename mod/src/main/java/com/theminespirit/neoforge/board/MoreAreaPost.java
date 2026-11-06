package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphTickets;

final class MoreAreaPost {
    private MoreAreaPost() {}

    static boolean handles(String node) {
        return More3Post.handles(node) || More4Post.handles(node);
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        return More3Post.handles(ticket.node()) ? More3Post.post(post, ticket) : More4Post.post(post, ticket);
    }
}
