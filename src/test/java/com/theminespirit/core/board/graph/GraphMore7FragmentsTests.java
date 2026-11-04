package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphMore7FragmentsTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();
    private static final List<String> EVENTS = List.of(
            More7Rules.MORNING_HARVEST,
            More7Rules.EVENING_RETURN,
            More7Rules.PEN_FULL,
            More7Rules.FIELD_FULL,
            More7Rules.ORDER_READY);
    private static final Map<String, String> WAYS = Map.ofEntries(
            Map.entry("more7_harvest_early", More7Rules.MORNING_HARVEST),
            Map.entry("more7_return_home", More7Rules.EVENING_RETURN),
            Map.entry("more7_expand_pen", More7Rules.PEN_FULL),
            Map.entry("more7_order_to_chest", More7Rules.ORDER_READY));
    private static final Set<String> IMPLEMENTED =
            Set.of("more7_harvest_early", "more7_expand_pen", "more7_return_home", "more7_order_to_chest");

    @Test
    void more7Events_FragmentLoad_AreDeclaredWithTasks() {
        for (String event : EVENTS) {
            assertTrue(GRAPH.events().containsKey(event), "Событие входа области graphmore7 объявлено: " + event);
        }
        for (String event : EVENTS) {
            GraphEvent known = GRAPH.events().get(event);
            if (known.tasks().isEmpty()) {
                assertFalse(known.push().isEmpty(), "Событие без задач толкает узел плана: " + event);
            } else {
                assertFalse(known.tasks().isEmpty(), "Событие ставит работу: " + event);
            }
        }
        for (String rule : List.of(
                "more7_spread_work",
                "more7_defer_gated",
                "more7_hold_herd",
                "more7_hold_sowing",
                "more7_expand_field")) {
            assertTrue(GRAPH.way(rule).isEmpty(), "Правило выбора живёт поправкой графа, а не путём: " + rule);
        }
    }

    @Test
    void more7Ways_FragmentLoad_AreWiredOrMarkedStubWithPositiveStartWeights() {
        for (Map.Entry<String, String> known : WAYS.entrySet()) {
            SupplyGraph.Way way = GRAPH.way(known.getKey()).orElseThrow();
            assertTrue(way.weight() > 0, "Стартовый вес пути положителен: " + known.getKey());
            assertTrue(way.boardCase() != null, "У пути есть вид работы: " + known.getKey());
            assertEquals(
                    IMPLEMENTED.contains(known.getKey()),
                    GRAPH.implemented(known.getKey()),
                    "Путь " + known.getKey() + " либо связан с действием, либо честно помечен заглушкой");
            assertTrue(GRAPH.producers(way.makes()).contains(way), "Путь делает свой узел области: " + known.getKey());
        }
    }

    @Test
    void more7Items_FragmentLoad_HavePositiveStartWeights() {
        for (String item :
                List.of("more7:harvest_peak", "more7:return_peak", "more7:pen_room", "more7:order_delivered")) {
            assertTrue(GRAPH.items().get(item).base() > 0, "Стартовый вес узла положителен: " + item);
        }
    }

    @Test
    void more7EventTasks_FragmentLoad_AllPointAtImplementedWays() {
        for (String event : EVENTS) {
            GraphEvent known = GRAPH.events().get(event);
            for (GraphEvent.Task task : known.tasks()) {
                assertTrue(
                        WAYS.containsKey(task.id()) || GRAPH.way(task.id()).isPresent(),
                        "Задача " + event + " -> " + task.id() + " должна принадлежать области");
                assertEquals(
                        GRAPH.implemented(task.id()),
                        task.implemented(),
                        "Задача " + event + " -> " + task.id()
                                + " не расходится с состоянием пути: иначе граф ставит неисполнимую работу");
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
