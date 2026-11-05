package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphReachTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();
    private static final GraphReach REACH =
            new GraphReach(GRAPH, GraphWorld.settled().state());

    @Test
    void producers_EveryWayNode_MatchGraphOrder() {
        Set<String> makes = new LinkedHashSet<>();
        GRAPH.ways().forEach(way -> makes.add(way.makes()));
        for (String node : makes) {
            assertEquals(
                    GRAPH.producers(node),
                    REACH.producers(node),
                    "Индекс обязан отдавать те же производители и в том же порядке, что и перебор путей графа: "
                            + "иначе планировщик выберет другой путь и задания разойдутся с прежними");
        }
    }

    @Test
    void producers_RepeatedLookup_ReturnsSameIndexedList() {
        String node = GRAPH.ways().stream()
                .map(SupplyGraph.Way::makes)
                .filter(makes -> GRAPH.producers(makes).size() > 1)
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("В графе нет предмета с несколькими путями: индекс не проверить"));
        List<SupplyGraph.Way> first = REACH.producers(node);
        assertSame(
                first,
                REACH.producers(node),
                "Повторный поиск производителей обязан брать готовый список из индекса, а не перебирать все пути: "
                        + "иначе план снова стоит «предметы × пути»");
        assertFalse(first.isEmpty(), "Индекс потерял производителей предмета " + node);
    }

    @Test
    void producers_UnknownNode_Empty() {
        assertTrue(
                REACH.producers("no_such_item").isEmpty(),
                "Неизвестный предмет не имеет производителей: пустой список, а не падение");
    }

    @Test
    void way_KnownAndUnknownId_FoundAndEmpty() {
        SupplyGraph.Way way = GRAPH.ways().getFirst();
        assertEquals(
                GRAPH.way(way.id()),
                REACH.way(way.id()),
                "Индекс путей обязан находить путь по id так же, как перебор графа");
        assertTrue(REACH.way("no_such_way").isEmpty(), "Неизвестный id не имеет пути: пустой Optional, а не падение");
    }
}
