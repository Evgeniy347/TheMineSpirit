package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.SupplyGraph;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphTicketIdsTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void ids_PenBuilding_PointToTheSpeciesTicket() {
        GraphPlan.Task task = task("building:pen_cow", BoardCase.BUILD_PEN, "pen_cow");

        assertEquals(
                List.of("cow_pen_1"),
                GraphTicketIds.ids(task, GRAPH),
                "Узел графа building:pen_cow исполняет задание загона коровы: приоритет графа обязан дойти "
                        + "до его билета, иначе работа выбирается без веса графа");
    }

    @Test
    void ids_RepairNode_PointsToTheRepairTicketOfItsBuilding() {
        GraphPlan.Task task = task("event:repair_hut:repair:hut", BoardCase.REPAIR_BUILDING, "repair:hut");

        assertEquals(
                List.of("repair:build_house"),
                GraphTicketIds.ids(task, GRAPH),
                "Узел графа repair:hut исполняет ремонт хижины: приоритет обязан дойти до билета repair:build_house");
    }

    private static GraphPlan.Task task(String id, BoardCase boardCase, String makes) {
        return new GraphPlan.Task(id, boardCase, makes, Optional.empty(), 50, Map.of(), true, false, true, 100);
    }
}
