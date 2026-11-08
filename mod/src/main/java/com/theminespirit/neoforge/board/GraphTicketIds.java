package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.RepairPlan;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.StageBuilt;
import com.theminespirit.core.board.graph.SupplyGraph;
import java.util.List;
import java.util.Locale;

final class GraphTicketIds {
    private GraphTicketIds() {}

    static List<String> ids(GraphPlan.Task task, SupplyGraph graph) {
        String id = GraphTickets.id(task);
        if (task.id().startsWith("building:") && StageBuilt.ids().contains(task.makes())) {
            return FirstStepsRunner.ticketIds(
                    graph.buildings().get(task.makes()).boardCase());
        }
        SupplyGraph.Building building = graph.buildings().get(task.makes());
        if (building != null && task.id().startsWith("building:")) {
            if (building.boardCase() == BoardCase.BUILD_MINE && task.makes().startsWith("mine_level_")) {
                return List.of(task.makes());
            }
            if (building.boardCase() == BoardCase.ENCHANT_ROOM) {
                return List.of(EnchantRoomWatch.ticketId(task.makes()));
            }
        }
        if (task.id().startsWith("event:") && task.makes().startsWith(WorldPost.REPAIR_NODE)) {
            SupplyGraph.Building damaged =
                    graph.buildings().get(task.makes().substring(WorldPost.REPAIR_NODE.length()));
            if (damaged != null) {
                String suffix = damaged.boardCase() == BoardCase.BUILD_PLACED ? ":" + damaged.id() : "";
                return List.of(RepairPlan.PREFIX + damaged.boardCase().name().toLowerCase(Locale.ROOT) + suffix);
            }
        }
        return List.of(id, id + GraphPost.PART);
    }
}
