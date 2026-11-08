package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RepairPlanTicketTests {
    @Test
    void ticket_PlanBuildings_HaveSeparateIds() {
        BoardTicket alchemy = RepairPlan.ticket(BoardCase.BUILD_PLACED, Map.of(), 1, "alchemy");
        BoardTicket kennel = RepairPlan.ticket(BoardCase.BUILD_PLACED, Map.of(), 1, "kennel");

        assertNotEquals(
                alchemy.id(),
                kennel.id(),
                "Две повреждённые постройки плана не могут делить один билет: иначе вторая ждёт первую "
                        + "(observe25 B9). id: " + alchemy.id() + " / " + kennel.id());
        assertEquals("repair:build_placed:alchemy", alchemy.id(), "Билет называет постройку плана");
    }

    @Test
    void ticket_StageBuilding_KeepsSharedIdPerCase() {
        assertEquals(
                "repair:build_house",
                RepairPlan.ticket(BoardCase.BUILD_HOUSE, Map.of(), 1).id(),
                "У постройки этапа один кейс и один билет: суффикс плана ей не нужен");
        assertEquals(
                "repair:build_house",
                RepairPlan.ticket(BoardCase.BUILD_HOUSE, Map.of(), 1, null).id(),
                "Без суффикса билет прежний: старые вызовы не меняют id");
    }
}
