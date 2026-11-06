package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class More2FragmentTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();
    private static final List<String> EVENTS = List.of(
            More2Rules.FOOD_LOW,
            More2Rules.INVENTORY_FULL,
            More2Rules.INVENTORY_SPACE,
            More2Rules.LONG_CARRY,
            More2Rules.FIRE_NEAR_BUILDING,
            More2Rules.LAVA_NEAR_BUILDING,
            More2Rules.HAZARD_FLOW,
            More2Rules.ANIMAL_OUTSIDE,
            More2Rules.WILD_HERD,
            More2Rules.VILLAGER_NEAR,
            More2Rules.STAGE_PREP,
            More2Rules.BED_NEEDED,
            More2Rules.PATH_NEEDED);

    @Test
    void more2Fragment_Events_AreDeclared() {
        for (String event : EVENTS) {
            assertTrue(GRAPH.events().containsKey(event), "Событие входа области graphmore2 объявлено: " + event);
        }
    }

    @Test
    void more2Fragment_ActionWays_AreImplementedOrControlSignals() {
        Map<String, String> implemented = Map.ofEntries(
                Map.entry("more2_sow_food", More2Rules.FOOD_LOW),
                Map.entry("more2_breed_food", More2Rules.FOOD_LOW),
                Map.entry("more2_unload_carry", More2Rules.INVENTORY_FULL),
                Map.entry("more2_put_out_fire", More2Rules.FIRE_NEAR_BUILDING),
                Map.entry("more2_remove_lava", More2Rules.LAVA_NEAR_BUILDING),
                Map.entry("more2_block_flow", More2Rules.HAZARD_FLOW),
                Map.entry("more2_pen_animal", More2Rules.ANIMAL_OUTSIDE),
                Map.entry("more2_lure_wild", More2Rules.WILD_HERD),
                Map.entry("more2_guard_villager", More2Rules.VILLAGER_NEAR),
                Map.entry("more2_prepare_stage", More2Rules.STAGE_PREP),
                Map.entry("more2_make_bed", More2Rules.BED_NEEDED),
                Map.entry("more2_build_trail", More2Rules.PATH_NEEDED));
        implemented.forEach((way, node) -> {
            assertTrue(GRAPH.way(way).isPresent(), "Путь действия объявлен: " + way);
            assertTrue(GRAPH.implemented(way), "Действие " + way + " связано с исполнением, узел " + node);
        });
        assertTrue(
                GRAPH.way("more2_trade_villager").isEmpty(),
                "Торговли у духов нет: узел без потребности удалён, а не висит заглушкой");
        assertFalse(GRAPH.events().containsKey("more2_villager_trade"), "Событие торговли удалено вместе с узлом");
        for (String duplicate : List.of("more2_load_carry", "more2_batch_carry")) {
            assertTrue(GRAPH.way(duplicate).isEmpty(), "Перенос и полевой сундук уже есть путями склада: " + duplicate);
        }
    }

    @Test
    void more2Fragment_EventTasks_RepeatWayImplementationFlag() {
        for (String event : EVENTS) {
            GraphEvent known = GRAPH.events().get(event);
            assertFalse(known.tasks().isEmpty(), "Событие " + event + " без задачи не двигает граф");
            for (GraphEvent.Task task : known.tasks()) {
                assertTrue(
                        GRAPH.way(task.id()).isPresent(),
                        "Задача " + event + " -> " + task.id() + " должна быть путём");
                assertEquals(
                        GRAPH.implemented(task.id()),
                        task.implemented(),
                        "Задача " + event + " -> " + task.id() + " обязана повторять флаг своего пути");
            }
        }
    }

    @Test
    void more2Fragment_ExistingEventTasks_StayImplemented() {
        assertTrue(
                GRAPH.events().get("crop_ready").tasks().get(0).implemented(),
                "Старые события без флага implemented остаются готовыми: сбор урожая уже работает");
    }

    @Test
    void more2Fragment_PushAndConsumes_ReferToKnownNodes() {
        for (String event : EVENTS) {
            GraphEvent known = GRAPH.events().get(event);
            known.push().keySet().forEach(node -> assertTrue(known(node), event + " push неизвестного " + node));
            known.tasks()
                    .forEach(task -> task.consumes()
                            .keySet()
                            .forEach(item -> assertTrue(known(item), event + " расходует неизвестное " + item)));
        }
    }

    @Test
    void more2Fragment_NewItemsAndBuildings_HaveStartWeights() {
        for (String item : List.of(
                "more2:food_plan",
                "more2:carry_room",
                "more2:safe",
                "more2:flow_blocked",
                "more2:animal_penned",
                "more2:village_safe",
                "more2:stage_ready",
                "more2:bed_ready",
                "more2:trail")) {
            assertTrue(GRAPH.items().get(item).base() > 0, "Стартовый вес узла положителен: " + item);
        }
        for (String building : List.of("more2_field_chest", "more2_trail")) {
            SupplyGraph.Building known = GRAPH.buildings().get(building);
            assertTrue(known != null && known.optional(), "Новая постройка необязательна: " + building);
            assertTrue(GRAPH.implemented(building), "Действие постройки связано с исполнением: " + building);
            assertTrue(known.base() > 0, "Стартовый вес постройки положителен: " + building);
        }
    }

    @Test
    void more2Fragment_AllEventNames_AreFromRules() {
        assertEquals(EVENTS.size(), EVENTS.stream().distinct().count(), "Имена событий области не повторяются");
        for (String event : EVENTS) {
            assertTrue(event.startsWith("more2_"), "Событие области названо с префиксом more2_: " + event);
        }
    }

    private static boolean known(String node) {
        if (node.startsWith("building:")) {
            return GRAPH.buildings().containsKey(node.substring("building:".length()));
        }
        return GRAPH.items().containsKey(node) || GRAPH.isGroup(node) || GRAPH.isTool(node);
    }
}
