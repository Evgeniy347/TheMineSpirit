package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphStageGateTests {
    @Test
    void simulate_StageTwoBaseline_ReachesStageThree() {
        GraphSimulation.Outcome outcome =
                GraphSimulation.runUntil(GraphMutations.baselines().get(3), 4000, 3);

        assertEquals(3, outcome.reachedStage(), "Этап 2 проходит честно, без обхода построек сторожа");
    }

    @Test
    void unbuilt_StageTwo_ContainsWarehouseAndPens() {
        GraphWorld world = GraphWorld.settled();
        world.stage = 2;
        GraphWorld.GRAPH.buildings().keySet().forEach(id -> world.buildings.put(id, GraphState.Built.PLANNED));

        assertTrue(GraphSimulation.unbuilt(world).contains("warehouse"), "склад строит граф, он держит этап");
        assertTrue(GraphSimulation.unbuilt(world).contains("pen_cow"), "загон строит граф, он держит этап");
    }

    @Test
    void building_NoMissingAndKnown_Ready() {
        GraphFacts facts = facts(building(Map.of()), GraphSiteFacts.Terrain.empty(), List.of());

        assertTrue(GraphStageGate.buildingReady(facts, "hut"), "целая известная постройка закрывает свой шаг этапа");
    }

    @Test
    void building_MissingBlocks_NotReady() {
        GraphFacts facts = facts(building(Map.of("minecraft:dirt", 3)), GraphSiteFacts.Terrain.empty(), List.of());

        assertFalse(GraphStageGate.buildingReady(facts, "hut"), "недостача блоков держит этап открытым");
    }

    @Test
    void building_UnknownChunks_NotReady() {
        GraphFacts.Building unknown = new GraphFacts.Building("hut", 1, 100, 0, Map.of(), Map.of(), 0, null, true);
        GraphFacts facts = facts(unknown, GraphSiteFacts.Terrain.empty(), List.of());

        assertFalse(GraphStageGate.buildingReady(facts, "hut"), "неизвестное — не готово: этап не закрывается");
    }

    @Test
    void lots_CutOrFill_NotReady() {
        assertFalse(
                GraphStageGate.lotsReady(
                        facts(building(Map.of()), terrain(Map.of("minecraft:dirt", 2), Map.of()), List.of())),
                "бугор на лоте держит этап открытым");
        assertFalse(
                GraphStageGate.lotsReady(
                        facts(building(Map.of()), terrain(Map.of(), Map.of("minecraft:dirt", 3)), List.of())),
                "яма на лоте держит этап открытым");
    }

    @Test
    void strays_PayerBuilt_DoNotBlock() {
        GraphFacts facts = facts(
                building(Map.of()),
                GraphSiteFacts.Terrain.empty(),
                List.of(new GraphSiteFacts.Stray("s", "oak_fence", new GraphFacts.Position(1, 64, 1), null)));

        assertTrue(GraphStageGate.straysReady(facts), "постройку игрока не сносим и этап ею не держим");
    }

    @Test
    void strays_SpiritBuilt_Block() {
        GraphFacts facts = facts(
                building(Map.of()),
                GraphSiteFacts.Terrain.empty(),
                List.of(new GraphSiteFacts.Stray("s", "oak_fence", new GraphFacts.Position(1, 64, 1), "spirit-1")));

        assertFalse(GraphStageGate.straysReady(facts), "лишняя постройка духа должна исчезнуть до закрытия этапа");
    }

    private static GraphFacts.Building building(Map<String, Integer> missing) {
        return new GraphFacts.Building("hut", 1, 90, 3, missing, Map.of(), 0, null, false);
    }

    private static GraphSiteFacts.Terrain terrain(Map<String, Integer> cut, Map<String, Integer> fill) {
        return new GraphSiteFacts.Terrain(0, 0, 0, 0, 0, cut, fill, Map.of());
    }

    private static GraphFacts facts(
            GraphFacts.Building building, GraphSiteFacts.Terrain terrain, List<GraphSiteFacts.Stray> strays) {
        return new GraphFacts(
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of("hut", building),
                strays,
                terrain,
                new GraphSiteFacts.StageReqs(Map.of("hut", 1), Map.of()));
    }
}
