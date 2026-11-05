package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EventTaskHandlersTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void gearEvents_TaskIdFromGraph_IsHandled() {
        for (String event : List.of(
                "gear_equip_best",
                "gear_craft_armor",
                "gear_craft_pickaxe",
                "gear_craft_axe",
                "gear_craft_shovel",
                "gear_craft_sword",
                "gear_craft_hoe")) {
            GraphEvent known = GRAPH.events().get(event);
            assertFalse(known == null || known.tasks().isEmpty(), "У события снаряжения нет задания: " + event);
            String task = known.tasks().get(0).id();
            assertTrue(
                    GearInputs.handlesTask(task),
                    "Обработчик снаряжения обязан узнавать задание события '" + event + "', а видит '" + task
                            + "': на доску приходит id задания, а не id события — иначе узел графа остаётся "
                            + "без исполнителя");
        }
    }

    @Test
    void storeEvents_TaskIdFromGraph_IsHandled() {
        for (String event :
                List.of("store_minimum", "transfer_pending", "storage_full", "storage_mixed", "furnace_ready")) {
            GraphEvent known = GRAPH.events().get(event);
            assertFalse(known == null || known.tasks().isEmpty(), "У события склада нет задания: " + event);
            String task = known.tasks().get(0).id();
            assertTrue(
                    StoreRules.handlesEvent(task),
                    "Обработчик склада обязан узнавать задание события '" + event + "', а видит '" + task
                            + "': перенос и сортировка иначе висят «без подключения к доске»");
        }
    }

    @Test
    void farmEvents_TaskIdFromGraph_IsHandled() {
        for (String event : List.of("crop_ready", "field_empty", "animal_starving")) {
            GraphEvent known = GRAPH.events().get(event);
            assertFalse(known == null || known.tasks().isEmpty(), "У события фермы нет задания: " + event);
            String task = known.tasks().get(0).id();
            assertTrue(
                    FarmRules.handlesEvent(task),
                    "Обработчик фермы обязан узнавать задание события '" + event + "', а видит '" + task
                            + "': посев и сбор иначе уходят в общий сбор предметов");
        }
    }
}
