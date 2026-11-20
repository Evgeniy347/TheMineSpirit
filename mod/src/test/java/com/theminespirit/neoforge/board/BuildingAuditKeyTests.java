package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BuildingAuditKeyTests {
    @Test
    void key_MainOrchard_OakBuilding() {
        assertEquals(
                "orchard_oak",
                BuildingAuditInputs.key("orchard"),
                "Главный сад — постройка orchard_oak: аудит сверяет его с дубовой схемой");
    }

    @Test
    void key_NumberedOrchard_BaseBuilding() {
        assertEquals(
                "orchard_jungle",
                BuildingAuditInputs.key("orchard_jungle_2"),
                "Второй участок породы аудируется под её базовой постройкой, а не отдельной незнакомой");
    }

    @Test
    void key_SpareOrchardSlot_NotOak() {
        assertEquals(
                "orchard_extra",
                BuildingAuditInputs.key("orchard_extra"),
                "Свободный слот без породы не считается дубовым садом: раньше он криво уходил в orchard_oak");
    }
}
