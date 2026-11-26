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
                        + "иначе падение прогресса ищут не там. Получено: " + why
                        + " При смене building_StreetsWithoutDirt_NamesMaterialShare проверь это утверждение.");
    }

    @Test
    void building_StreetsBeforeHut_NamesDependency() {
        GraphState state = state(Map.of("dirt", 40), Map.of());

        String why = GraphWhy.building(GRAPH, state, "streets");

        assertTrue(
                why.contains("не готова hut"),
                "Улицы ждут хижину: причина «?» — незакрытая зависимость графа. Получено: " + why
                        + " Иначе тест building_StreetsBeforeHut_NamesDependency перестанет ловить эту поломку. При смене building_StreetsBeforeHut_NamesDependency проверь это утверждение.");
    }

    @Test
    void building_MineWithoutPickaxe_NamesTool() {
        GraphState state = state(Map.of("torch", 6), Map.of("clearing", GraphState.Built.BUILT));

        String why = GraphWhy.building(GRAPH, state, "mine");

        assertTrue(
                why.contains("нет инструмента pickaxe"),
                "Шахте нужна кирка: «?» без инструмента объясняется инструментом. Получено: " + why
                        + " Иначе тест building_MineWithoutPickaxe_NamesTool перестанет ловить эту поломку. При смене building_MineWithoutPickaxe_NamesTool проверь это утверждение.");
    }

    @Test
    void building_EmptyActualBillWithoutStartStock_NamesStaticShare() {
        GraphState state = new GraphState(
                Map.of(), 1, Map.of(), Map.of(), Map.of("wheat_field", Map.of()), 50, Set.of(), Set.of());

        String why = GraphWhy.building(GRAPH, state, "wheat_field");

        assertTrue(
                why.contains("стартовый набор материалов 0% < 25%"),
                "Пустая фактическая смета не скрывает отдельный стартовый порог графа: иначе обязательное поле "
                        + "снова блокируется сообщением «граф не назвал причину». Получено: " + why
                        + " При смене building_EmptyActualBillWithoutStartStock_NamesStaticShare проверь GraphTaskBuilder "
                        + "и живой wheat_field.");
    }

    @Test
    void building_UnknownId_SaysNotInGraph() {
        assertEquals(
                "постройки ghost нет в графе",
                GraphWhy.building(GRAPH, state(Map.of(), Map.of()), "ghost"),
                "Неизвестная постройка тоже обязана получить причину, а не пустое «?»"
                        + " Иначе тест building_UnknownId_SaysNotInGraph перестанет ловить эту поломку. При смене building_UnknownId_SaysNotInGraph проверь это утверждение.");
    }

    private static GraphState state(Map<String, Integer> stock, Map<String, GraphState.Built> buildings) {
        return new GraphState(stock, 1, Map.of(), buildings, Map.of(), 50, Set.of(), Set.of());
    }
}
