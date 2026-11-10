package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphBuildingMaterialsTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void materials_MissingBlocks_BecomeItems() {
        GraphFacts facts = facts(
                new GraphFacts.Building("hut", 1, 75, 3, Map.of("minecraft:dirt", 3), Map.of(), 0, null, false, true));

        Map<String, Map<String, Integer>> materials = GraphBuildingMaterials.of(facts, GRAPH);

        assertEquals(
                Map.of("dirt", 3),
                materials.get("hut"),
                "недостающие блоки через blockItems превращаются в предметы для ремонта");
    }

    @Test
    void materials_UnknownChunks_GiveNothing() {
        GraphFacts facts = facts(
                new GraphFacts.Building("hut", 1, 0, 0, Map.of("minecraft:dirt", 3), Map.of(), 0, null, true, true));

        assertTrue(
                GraphBuildingMaterials.of(facts, GRAPH).isEmpty(),
                "пока чанки не загружены, материалы не выдумываются: ремонт не должен начинаться вслепую");
    }

    @Test
    void materials_CleanBuilding_GivesNothing() {
        GraphFacts facts = facts(new GraphFacts.Building(
                "hut", 1, 100, 0, Map.of(), Map.of("minecraft:stone", 1), 0, null, false, true));

        assertTrue(
                GraphBuildingMaterials.of(facts, GRAPH).isEmpty(),
                "лишнее убирают отдельным путём ремонта, а не закупкой материалов: недостачи нет");
    }

    @Test
    void merged_PartialBuilding_AuditMissingWinsOverScheme() {
        GraphFacts facts = facts(
                new GraphFacts.Building("hut", 1, 95, 5, Map.of("minecraft:dirt", 5), Map.of(), 0, null, false, true));

        Map<String, Map<String, Integer>> merged =
                GraphBuildingMaterials.merged(Map.of("hut", Map.of("oak_log", 12, "oak_planks", 30)), facts, GRAPH);

        assertEquals(
                Map.of("dirt", 5),
                merged.get("hut"),
                "Частично построенный дом ремонтируют недостающими блоками аудита: полный набор схемы "
                        + "заставил бы духа тащить лишнее и снова строить целое");
    }

    @Test
    void merged_NotStartedBuilding_KeepsDeclaredScheme() {
        GraphFacts facts = facts(
                new GraphFacts.Building("hut", 1, 0, 0, Map.of("minecraft:dirt", 30), Map.of(), 0, null, false, true));

        Map<String, Map<String, Integer>> merged =
                GraphBuildingMaterials.merged(Map.of("hut", Map.of("oak_log", 12, "oak_planks", 30)), facts, GRAPH);

        assertEquals(
                Map.of("oak_log", 12, "oak_planks", 30),
                merged.get("hut"),
                "Не начатая постройка строится по объявленному набору схемы: аудит нуля не заменяет стройку");
    }

    private static GraphFacts facts(GraphFacts.Building building) {
        return new GraphFacts(
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(building.id(), building),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()),
                GraphFeedback.none(),
                List.of(),
                List.of(),
                List.of());
    }
}
