package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.graph.GraphTickets;
import java.util.ArrayList;
import java.util.List;

final class GraphPosting {
    static final int TICKET_BUDGET = 8;

    private final GraphSource.Half half;
    private final GraphPost post;
    private final List<GraphTickets.Ticket> tickets;
    private final List<String> unconnected = new ArrayList<>();
    private int cursor;

    GraphPosting(GraphSource.Half half, PetWork pets) {
        this.half = half;
        this.post = new GraphPost(
                half.level(),
                half.service(),
                half.spirits(),
                half.stock(),
                new GraphPost.Site(half.scan(), half.around(), pets, half.state()),
                half.graph(),
                half.now());
        this.tickets = half.result().post();
        GraphStanding.seed(post, half.plan().tasks(), half.result().live());
    }

    boolean advance() {
        int end = Math.min(tickets.size(), cursor + TICKET_BUDGET);
        while (cursor < end) {
            GraphTickets.Ticket ticket = tickets.get(cursor++);
            BoardStatus status = half.service().board().statusOf(ticket.id()).orElse(null);
            boolean activity = BoardActivities.isActivity(ticket.task().boardCase());
            if (status == BoardStatus.DONE) {
                if (activity) {
                    continue;
                }
                half.service().board().reopen(ticket.id());
            } else if (status == BoardStatus.BLOCKED) {
                if (!half.service().board().failures().unblock(ticket.id(), half.now())) {
                    continue;
                }
            } else if (status != null && !activity) {
                continue;
            } else if (activity && GraphSource.closed(status)) {
                continue;
            }
            if (!TicketIsolation.post(ticket.id(), () -> post.post(ticket))) {
                if (!post.waiting().contains(ticket.id())) {
                    unconnected.add(ticket.id());
                }
            }
        }
        return cursor >= tickets.size();
    }

    GraphSource.Half half() {
        return half;
    }

    GraphPost post() {
        return post;
    }

    List<String> unconnected() {
        return List.copyOf(unconnected);
    }
}
