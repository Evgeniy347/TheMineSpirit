package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.SupplyGraph;
import java.util.Map;

final class GraphNodeBuilds {
    private GraphNodeBuilds() {}

    static boolean post(GraphPost post, GraphTickets.Ticket ticket, GraphPlan.Task task) {
        if (WarehouseWatch.handles(ticket.node())) {
            return WarehouseWatch.post(post, ticket);
        }
        SupplyGraph.Building building = post.graph.buildings().get(ticket.node());
        if (building == null) {
            return false;
        }
        BoardCase boardCase = building.boardCase();
        Map<String, Integer> need = WorldPost.need(post.graph, task.consumes(), post.scan.wood());
        return switch (boardCase) {
            case BUILD_MINE ->
                ticket.node().startsWith("mine_level_")
                        && MineLevels.post(
                                post.level, post.service, post.spirits, ticket.node(), task.priority(), need, post.now);
            case ENCHANT_ROOM ->
                EnchantRoomWatch.post(
                        post.level, post.service, post.spirits, ticket.node(), task.priority(), need, post.now);
            default -> false;
        };
    }
}
