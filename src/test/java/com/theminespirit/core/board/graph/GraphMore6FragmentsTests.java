package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GraphMore6FragmentsTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();
    private static final List<String> EVENTS = List.of(
            More6Rules.SPIRITS_SHORT,
            More6Rules.WEAK_FAR,
            More6Rules.BUILD_UNFINISHED,
            More6Rules.CARRY_MATERIALS,
            More6Rules.STATION_BROKEN,
            More6Rules.LOOSE_ITEMS_FOUND,
            More6Rules.WATER_NEEDED,
            More6Rules.LAVA_NEEDED,
            More6Rules.REPEAT_DEMAND,
            More6Rules.SPIRIT_IDLE);
    private static final List<String> WAYS = List.of(
            "more6_call_spirit",
            "more6_return_weak",
            "more6_send_near",
            "more6_finish_build",
            "more6_deposit_materials",
            "more6_refuel_station",
            "more6_place_station",
            "more6_pickup_items",
            "more6_fetch_water",
            "more6_fetch_lava",
            "more6_maintain_stock",
            "more6_assign_idle");

    @Test
    void more6Events_FragmentLoad_AreDeclared() {
        for (String event : EVENTS) {
            assertTrue(GRAPH.events().containsKey(event), "Событие входа области more6 объявлено: " + event);
            assertFalse(GRAPH.events().get(event).tasks().isEmpty(), "Событие ставит работу: " + event);
        }
    }

    @Test
    void more6Ways_FragmentLoad_AreStubbedWithTodo() {
        for (String way : WAYS) {
            SupplyGraph.Way known = GRAPH.way(way).orElseThrow();
            assertTrue(known.weight() > 0, "Стартовый вес пути положителен: " + way);
            assertFalse(GRAPH.implemented(way), "Действия пока нет — путь заглушён: " + way);
        }
    }

    @Test
    void more6Items_FragmentLoad_HavePositiveStartWeights() {
        for (String item : List.of(
                "more6:more_spirits",
                "more6:weak_spirit_home",
                "more6:building_finished",
                "more6:materials_deposited",
                "more6:station_ready",
                "more6:ground_cleared",
                "more6:water_source",
                "more6:fuel_reserve",
                "more6:stock_reserve",
                "more6:spirit_busy")) {
            assertTrue(GRAPH.items().get(item).base() > 0, "Стартовый вес узла положителен: " + item);
        }
    }

    @Test
    void more6Events_FragmentLoad_PushKnownItems() {
        for (String event : EVENTS) {
            GraphEvent known = GRAPH.events().get(event);
            known.push()
                    .keySet()
                    .forEach(node -> assertTrue(
                            GRAPH.items().containsKey(node) || GRAPH.isGroup(node),
                            "Событие " + event + " толкает неизвестный узел: " + node));
        }
    }

    @Test
    void more6Events_FragmentLoad_TasksAreStubs() {
        for (String event : EVENTS) {
            GraphEvent known = GRAPH.events().get(event);
            for (GraphEvent.Task task : known.tasks()) {
                assertFalse(task.implemented(), "Задача события пока заглушена: " + event + " -> " + task.id());
            }
        }
    }

    @Test
    void more6Items_FragmentLoad_AreNotDemandedByDefault() {
        for (String item : List.of("more6:more_spirits", "more6:weak_spirit_home", "more6:spirit_busy")) {
            assertEquals(0, GRAPH.items().get(item).xmin(), "Вход области не создаёт спрос сам по себе: " + item);
        }
    }
}
