package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphStateExportTests {
    @Test
    void state_Export_ContainsNewFields() throws JsonProcessingException {
        GraphFacts facts = new GraphFacts(
                Map.of(
                        "spirit-1",
                        new GraphFacts.Spirit(
                                "spirit-1",
                                new GraphFacts.Position(1, 64, 2),
                                20,
                                18,
                                300,
                                false,
                                false,
                                true,
                                "supply_gather",
                                Map.of("minecraft:oak_log", 2),
                                List.of(new GraphFacts.Armor("HEAD", "minecraft:iron_helmet", 100, 165)),
                                List.of(new GraphFacts.Tool("minecraft:iron_pickaxe", "pickaxe", 2, 200, 250)),
                                null)),
                List.of(),
                List.of(new GraphFacts.Chest(new GraphFacts.Position(3, 64, 4), Map.of("minecraft:coal", 8), 5, 27)),
                List.of(new GraphFacts.Resource(
                        "tree:oak", new GraphFacts.Position(5, 64, 6), 7, GraphFacts.Reach.UNKNOWN, 14)),
                Map.of("furnace", new GraphFacts.Station("furnace", "furnace", null, false, null, null, null)),
                Map.of("hut", new GraphFacts.Building("hut", 1, 100, 0, 0, 0, null, false)),
                List.of(),
                GraphFacts.Terrain.empty(),
                new GraphFacts.StageReqs(Map.of("hut", 1), Map.of("hut", 1)));
        GraphState state = GraphWorld.empty().state().withFacts(facts).withOrders(Map.of("coal", 3));

        String json = new ObjectMapper().writeValueAsString(GraphExportTests.state(state));

        assertTrue(json.contains("\"facts\""), "Выгрузка обязана содержать новые факты графа");
        assertTrue(json.contains("\"spirit-1\""), "Выгрузка обязана содержать факты духа");
        assertTrue(json.contains("\"auditPercent\""), "Выгрузка обязана содержать факт аудита постройки");
        assertTrue(json.contains("\"resources\""), "Выгрузка обязана содержать ресурсы с расстоянием");
        assertTrue(json.contains("\"distances\""), "Выгрузка обязана содержать расстояния для модели");
        assertTrue(json.contains("\"stations\""), "Выгрузка обязана содержать станции для модели");
        assertTrue(json.contains("\"orders\""), "Выгрузка обязана содержать заказы игрока");
    }
}
