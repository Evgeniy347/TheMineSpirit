package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.StageBuilt;
import com.theminespirit.core.board.graph.SupplyGraph;
import java.util.List;

final class GraphTicketIds {
    private GraphTicketIds() {}

    static List<String> ids(GraphPlan.Task task, SupplyGraph graph) {
        String id = GraphTickets.id(task);
        if (task.id().startsWith("building:") && StageBuilt.ids().contains(task.makes())) {
            return FirstStepsRunner.ticketIds(
                    graph.buildings().get(task.makes()).boardCase());
        }
        return List.of(id, id + GraphPost.PART);
    }
}
