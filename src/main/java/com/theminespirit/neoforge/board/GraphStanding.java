package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import java.util.List;
import java.util.Set;

final class GraphStanding {
    private GraphStanding() {}

    static void seed(GraphPost post, List<GraphPlan.Task> tasks, Set<String> live) {
        for (GraphPlan.Task task : tasks) {
            if (!task.implemented() || task.priority() <= 0 || !live.contains(GraphTickets.id(task))) {
                continue;
            }
            BoardStatus status =
                    post.service.board().statusOf(GraphTickets.id(task)).orElse(null);
            if (status == null
                    || status == BoardStatus.DONE
                    || status == BoardStatus.FAILED
                    || status == BoardStatus.CANCELLED) {
                continue;
            }
            post.demands.standing(task, post.craftPoint());
        }
    }
}
