package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphWhyTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void building_StreetsWithoutDirt_NamesMaterialShare() {
        GraphState state = state(Map.of(), Map.of("hut", GraphState.Built.BUILT));

        String why = GraphWhy.building(GRAPH, state, "streets");

        assertTrue(
                why.contains("материалов 0% < 25%"),
                "«?» у streets означает нехватку материалов, а не «аудит не знает»: причина обязана быть в строке, "
                        + "иначе падение прогресса ищут не там. Получено: " + why);
    }

    @Test
    void building_StreetsBeforeHut_NamesDependency() {
        GraphState state = state(Map.of("dirt", 40), Map.of());

        String why = GraphWhy.building(GRAPH, state, "streets");

        assertTrue(
                why.contains("не готова hut"),
                "Улицы ждут хижину: причина «?» — незакрытая зависимость графа. Получено: " + why);
    }

    @Test
    void building_MineWithoutPickaxe_NamesTool() {
        GraphState state = state(Map.of("torch", 6), Map.of("clearing", GraphState.Built.BUILT));

        String why = GraphWhy.building(GRAPH, state, "mine");

        assertTrue(
                why.contains("нет инструмента pickaxe"),
                "Шахте нужна кирка: «?» без инструмента объясняется инструментом. Получено: " + why);
    }

    @Test
    void building_UnknownId_SaysNotInGraph() {
        assertEquals(
                "постройки ghost нет в графе",
                GraphWhy.building(GRAPH, state(Map.of(), Map.of()), "ghost"),
                "Неизвестная постройка тоже обязана получить причину, а не пустое «?»");
    }

    private static GraphState state(Map<String, Integer> stock, Map<String, GraphState.Built> buildings) {
        return new GraphState(stock, 1, Map.of(), buildings, Map.of(), 50, Set.of(), Set.of());
    }
}
