package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.log.Perf;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

final class GraphPosting {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final int TICKET_BUDGET = 8;
    static final int PRIORITY_BUDGET = 32;

    private enum Phase {
        POST,
        FLUSH,
        PRIORITIES,
        DONE
    }

    private record Priority(String id, int value) {}

    private final GraphSource.Half half;
    private final GraphPost post;
    private final List<GraphTickets.Ticket> tickets;
    private final List<Priority> priorities = new ArrayList<>();
    private final List<String> unconnected = new ArrayList<>();
    private int cursor;
    private int priorityCursor;
    private Phase phase = Phase.POST;

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
        for (GraphPlan.Task task : half.plan().tasks()) {
            if (task.implemented() && task.priority() > 0) {
                for (String id : GraphTicketIds.ids(task, half.graph())) {
                    priorities.add(new Priority(id, task.priority()));
                }
            }
        }
    }

    boolean advance() {
        return switch (phase) {
            case POST -> postTickets();
            case FLUSH -> flush();
            case PRIORITIES -> updatePriorities();
            case DONE -> true;
        };
    }

    private boolean postTickets() {
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
        if (cursor >= tickets.size()) {
            phase = Phase.FLUSH;
        }
        return false;
    }

    private boolean flush() {
        long started = System.nanoTime();
        post.flush();
        StageWorldGate.apply(
                half.level(),
                half.service(),
                half.scan().stage(),
                half.scan().wood(),
                half.state().facts());
        Perf.record("graph.src.finish.flush", System.nanoTime() - started);
        phase = Phase.PRIORITIES;
        return false;
    }

    private boolean updatePriorities() {
        long started = System.nanoTime();
        int end = Math.min(priorities.size(), priorityCursor + PRIORITY_BUDGET);
        while (priorityCursor < end) {
            Priority priority = priorities.get(priorityCursor++);
            try {
                half.service().board().reprioritize(priority.id(), priority.value());
            } catch (RuntimeException error) {
                LOGGER.error(
                        "[incident] граф не смог обновить приоритет задания {} до {}: {}; "
                                + "остальные задания и тик сервера идут",
                        priority.id(),
                        priority.value(),
                        String.valueOf(error.getMessage()),
                        error);
            }
        }
        Perf.record("graph.src.finish.priorities", System.nanoTime() - started);
        if (priorityCursor >= priorities.size()) {
            phase = Phase.DONE;
            return true;
        }
        return false;
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
