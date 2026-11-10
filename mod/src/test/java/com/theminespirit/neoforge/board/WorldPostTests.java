package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class WorldPostTests {
    @Test
    void handles_WorldCleanupAndRepairNodes_AreRecognized() {
        assertFalse(
                WorldPost.handles("demolish_stray"),
                "Снос лишнего уже исполняет PlanDemolition по узлу графа, а не заглушка мира");
        assertTrue(WorldPost.handles("backfill"), "Засыпка — узел мира");
        assertFalse(
                WorldPost.handles("clear_floating"),
                "Уборку висящего исполняет PlanDemolition по узлу графа, а не заглушка мира");
        assertTrue(WorldPost.handles("level_lot"), "Выравнивание — узел мира");
        assertTrue(WorldPost.handles("restore_trace"), "Возврат следа — узел мира");
        assertTrue(WorldPost.handles("remove_route"), "Снятие старой тропы — узел мира");
        assertTrue(WorldPost.handles("repair:hut"), "Ремонт хижины — узел мира");
        assertTrue(WorldPost.handles("repair:clearing"), "Ремонт расчистки — узел мира");
        assertTrue(WorldPost.handles("repair:mine"), "Ремонт шахты — узел мира");
        assertFalse(WorldPost.handles("collect_drops"), "Чужой узел не перехватывается");
    }

    @Test
    void room_OpenBatch_LeavesOnlyMissingSlots() {
        assertEquals(8, WorldPost.room(0, 8), "пустая доска даёт всю порцию уборки");
        assertEquals(3, WorldPost.room(5, 8), "уже стоящие задания занимают места порции");
        assertEquals(
                0,
                WorldPost.room(9, 8),
                "порция не растёт: иначе 95 world:backfill забивают доску, и вытеснение снимает "
                        + "repair:build_mine с причиной always");
    }

    @Test
    void placedTarget_RepairOfPlacedBuilding_CarriesSchemeSlotAndWood() {
        BlockCoord yard = new BlockCoord(0, 64, 0);
        BlockCoord slot = new BlockCoord(10, 64, 20);
        TicketTarget target = PlacedRepair.target(
                slot, "schematics/well", "well", TicketTarget.at(yard).withWood("jungle"));

        assertEquals("schematics/well", target.schemeFile(), "без схемы план BUILD_PLACED падает и останавливает граф");
        assertEquals(slot, target.target(), "ремонт идёт в слот постройки, а не на рабочий двор");
        assertEquals("well", target.building(), "задание помнит свою постройку плана");
        assertEquals("jungle", target.wood(), "порода берётся из общей цели этапа");
    }
}
