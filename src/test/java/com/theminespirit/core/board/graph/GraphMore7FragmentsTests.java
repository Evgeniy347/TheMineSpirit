package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphMore7FragmentsTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();
    private static final List<String> EVENTS = List.of(
            More7Rules.WORK_PILEUP,
            More7Rules.GATED_ITEM,
            More7Rules.MORNING_HARVEST,
            More7Rules.EVENING_RETURN,
            More7Rules.PEN_FULL,
            More7Rules.FIELD_FULL,
            More7Rules.ORDER_READY);
    private static final Map<String, String> STUBS = Map.ofEntries(
            Map.entry("more7_spread_work", More7Rules.WORK_PILEUP),
            Map.entry("more7_defer_gated", More7Rules.GATED_ITEM),
            Map.entry("more7_harvest_early", More7Rules.MORNING_HARVEST),
            Map.entry("more7_return_home", More7Rules.EVENING_RETURN),
            Map.entry("more7_expand_pen", More7Rules.PEN_FULL),
            Map.entry("more7_hold_herd", More7Rules.PEN_FULL),
            Map.entry("more7_expand_field", More7Rules.FIELD_FULL),
            Map.entry("more7_hold_sowing", More7Rules.FIELD_FULL),
            Map.entry("more7_order_to_player", More7Rules.ORDER_READY),
            Map.entry("more7_order_to_chest", More7Rules.ORDER_READY));

    @Test
    void more7Events_FragmentLoad_AreDeclaredWithTasks() {
        for (String event : EVENTS) {
            assertTrue(GRAPH.events().containsKey(event), "Событие входа области graphmore7 объявлено: " + event);
            assertFalse(GRAPH.events().get(event).tasks().isEmpty(), "Событие ставит работу: " + event);
        }
    }

    @Test
    void more7Ways_FragmentLoad_AreStubsWithPositiveStartWeights() {
        for (Map.Entry<String, String> stub : STUBS.entrySet()) {
            SupplyGraph.Way way = GRAPH.way(stub.getKey()).orElseThrow();
            assertTrue(way.weight() > 0, "Стартовый вес пути положителен: " + stub.getKey());
            assertTrue(way.boardCase() != null, "У пути есть вид работы: " + stub.getKey());
            assertFalse(GRAPH.implemented(stub.getKey()), "Действие пока заглушено: " + stub.getKey());
            assertTrue(
                    GRAPH.producers(way.makes()).contains(way), "Заглушка делает свой узел области: " + stub.getKey());
        }
    }

    @Test
    void more7Items_FragmentLoad_HavePositiveStartWeights() {
        for (String item : List.of(
                "more7:work_split",
                "more7:stage_gated",
                "more7:harvest_peak",
                "more7:return_peak",
                "more7:pen_room",
                "more7:field_room",
                "more7:order_delivered")) {
            assertTrue(GRAPH.items().get(item).base() > 0, "Стартовый вес узла положителен: " + item);
        }
    }

    @Test
    void more7EventTasks_FragmentLoad_AllPointAtStubWays() {
        for (String event : EVENTS) {
            GraphEvent known = GRAPH.events().get(event);
            for (GraphEvent.Task task : known.tasks()) {
                assertTrue(
                        STUBS.containsKey(task.id()),
                        "Задача " + event + " -> " + task.id() + " должна быть заглушкой области");
                assertFalse(task.implemented(), "Задача " + event + " -> " + task.id() + " ещё не реализована");
            }
            known.push().keySet().forEach(node -> assertTrue(known(node), event + " push неизвестного " + node));
            known.tasks()
                    .forEach(task -> task.consumes()
                            .keySet()
                            .forEach(item -> assertTrue(known(item), event + " расходует неизвестное " + item)));
        }
    }

    @Test
    void more7EventNames_FragmentLoad_AreUniqueAndPrefixed() {
        assertEquals(EVENTS.size(), EVENTS.stream().distinct().count(), "Имена событий области не повторяются");
        for (String event : EVENTS) {
            assertTrue(event.startsWith("more7_"), "Событие области названо с префиксом more7_: " + event);
        }
    }

    private static boolean known(String node) {
        if (node.startsWith("building:")) {
            return GRAPH.buildings().containsKey(node.substring("building:".length()));
        }
        return GRAPH.items().containsKey(node) || GRAPH.isGroup(node) || GRAPH.isTool(node);
    }
}
