package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphPlan;
import java.util.List;
import java.util.Set;

final class GraphStanding {
    private GraphStanding() {}

    static void seed(GraphPost post, List<GraphPlan.Task> tasks, Set<String> live) {
        for (GraphPlan.Task task :
                GraphPlan.standing(tasks, live, id -> post.service.board().statusOf(id))) {
            post.demands.standing(task, post.craftPoint());
        }
    }
}
