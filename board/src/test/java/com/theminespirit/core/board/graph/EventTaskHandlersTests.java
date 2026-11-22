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
            assertFalse(
                    known == null || known.tasks().isEmpty(),
                    "У события снаряжения нет задания: " + event
                            + " Иначе тест gearEvents_TaskIdFromGraph_IsHandled перестанет ловить эту поломку. При смене gearEvents_TaskIdFromGraph_IsHandled проверь это утверждение.");
            String task = known.tasks().get(0).id();
            assertTrue(
                    GearInputs.handlesTask(task),
                    "Обработчик снаряжения обязан узнавать задание события '" + event + "', а видит '" + task
                            + "': на доску приходит id задания, а не id события — иначе узел графа остаётся "
                            + "без исполнителя"
                            + " При смене gearEvents_TaskIdFromGraph_IsHandled проверь это утверждение.");
        }
    }

    @Test
    void storeEvents_TaskIdFromGraph_IsHandled() {
        for (String event : List.of("store_minimum", "transfer_pending", "storage_mixed", "furnace_ready")) {
            GraphEvent known = GRAPH.events().get(event);
            assertFalse(
                    known == null || known.tasks().isEmpty(),
                    "У события склада нет задания: " + event
                            + " Иначе тест storeEvents_TaskIdFromGraph_IsHandled перестанет ловить эту поломку. При смене storeEvents_TaskIdFromGraph_IsHandled проверь это утверждение.");
            String task = known.tasks().get(0).id();
            assertTrue(
                    StoreRules.handlesEvent(task),
                    "Обработчик склада обязан узнавать задание события '" + event + "', а видит '" + task
                            + "': перенос и сортировка иначе висят «без подключения к доске»"
                            + " При смене storeEvents_TaskIdFromGraph_IsHandled проверь это утверждение.");
        }
    }

    @Test
    void farmEvents_TaskIdFromGraph_IsHandled() {
        for (String event : List.of("crop_ready", "field_empty", "animal_starving")) {
            GraphEvent known = GRAPH.events().get(event);
            assertFalse(
                    known == null || known.tasks().isEmpty(),
                    "У события фермы нет задания: " + event
                            + " Иначе тест farmEvents_TaskIdFromGraph_IsHandled перестанет ловить эту поломку. При смене farmEvents_TaskIdFromGraph_IsHandled проверь это утверждение.");
            String task = known.tasks().get(0).id();
            assertTrue(
                    FarmRules.handlesEvent(task),
                    "Обработчик фермы обязан узнавать задание события '" + event + "', а видит '" + task
                            + "': посев и сбор иначе уходят в общий сбор предметов"
                            + " При смене farmEvents_TaskIdFromGraph_IsHandled проверь это утверждение.");
        }
    }
}
