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

        assertEquals(
                3,
                outcome.reachedStage(),
                "Этап 2 проходит честно, без обхода построек сторожа"
                        + " Иначе тест simulate_StageTwoBaseline_ReachesStageThree перестанет ловить эту поломку. При смене simulate_StageTwoBaseline_ReachesStageThree проверь это утверждение.");
    }

    @Test
    void unbuilt_StageTwo_ContainsWarehouseAndPens() {
        GraphWorld world = GraphWorld.settled();
        world.stage = 2;
        GraphWorld.GRAPH.buildings().keySet().forEach(id -> world.buildings.put(id, GraphState.Built.PLANNED));

        assertTrue(
                GraphSimulation.unbuilt(world).contains("warehouse"),
                "склад строит граф, он держит этап"
                        + " Иначе тест unbuilt_StageTwo_ContainsWarehouseAndPens перестанет ловить эту поломку. При смене unbuilt_StageTwo_ContainsWarehouseAndPens проверь это утверждение.");
        assertTrue(
                GraphSimulation.unbuilt(world).contains("pen_cow"),
                "загон строит граф, он держит этап"
                        + " Иначе тест unbuilt_StageTwo_ContainsWarehouseAndPens перестанет ловить эту поломку. При смене unbuilt_StageTwo_ContainsWarehouseAndPens проверь это утверждение.");
    }

    @Test
    void building_NoMissingAndKnown_Ready() {
        GraphFacts facts = facts(building(Map.of()), GraphSiteFacts.Terrain.empty(), List.of());

        assertTrue(
                GraphStageGate.buildingReady(facts, "hut"),
                "целая известная постройка закрывает свой шаг этапа"
                        + " Иначе тест building_NoMissingAndKnown_Ready перестанет ловить эту поломку. При смене building_NoMissingAndKnown_Ready проверь это утверждение.");
    }

    @Test
    void building_MissingBlocks_NotReady() {
        GraphFacts facts = facts(building(Map.of("minecraft:dirt", 3)), GraphSiteFacts.Terrain.empty(), List.of());

        assertFalse(
                GraphStageGate.buildingReady(facts, "hut"),
                "недостача блоков держит этап открытым"
                        + " Иначе тест building_MissingBlocks_NotReady перестанет ловить эту поломку. При смене building_MissingBlocks_NotReady проверь это утверждение.");
    }

    @Test
    void building_DamagedOrIncomplete_NotReady() {
        GraphFacts damaged = facts(
                new GraphFacts.Building("hut", 1, 100, 2, Map.of(), Map.of("minecraft:dirt", 2), 0, null, false, true),
                GraphSiteFacts.Terrain.empty(),
                List.of());
        GraphFacts partial = facts(
                new GraphFacts.Building("hut", 1, 99, 1, Map.of(), Map.of(), 0, null, false, true),
                GraphSiteFacts.Terrain.empty(),
                List.of());

        assertFalse(
                GraphStageGate.buildingReady(damaged, "hut"),
                "Повреждённая постройка (чужое/лишнее) не закрывает этап, даже если план поставлен на 100%"
                        + " Иначе тест building_DamagedOrIncomplete_NotReady перестанет ловить эту поломку. При смене building_DamagedOrIncomplete_NotReady проверь это утверждение.");
        assertFalse(
                GraphStageGate.buildingReady(partial, "hut"),
                "Готовность ниже 100% не закрывает этап"
                        + " Иначе тест building_DamagedOrIncomplete_NotReady перестанет ловить эту поломку. При смене building_DamagedOrIncomplete_NotReady проверь это утверждение.");
    }

    @Test
    void building_UnknownChunks_NotReady() {
        GraphFacts.Building unknown =
                new GraphFacts.Building("hut", 1, 100, 0, Map.of(), Map.of(), 0, null, true, true);
        GraphFacts facts = facts(unknown, GraphSiteFacts.Terrain.empty(), List.of());

        assertFalse(
                GraphStageGate.buildingReady(facts, "hut"),
                "неизвестное — не готово: этап не закрывается"
                        + " Иначе тест building_UnknownChunks_NotReady перестанет ловить эту поломку. При смене building_UnknownChunks_NotReady проверь это утверждение.");
    }

    @Test
    void lots_CutOrFill_NotReady() {
        assertFalse(
                GraphStageGate.lotsReady(
                        facts(building(Map.of()), terrain(Map.of("minecraft:dirt", 2), Map.of()), List.of())),
                "бугор на лоте держит этап открытым"
                        + " Иначе тест lots_CutOrFill_NotReady перестанет ловить эту поломку. При смене lots_CutOrFill_NotReady проверь это утверждение.");
        assertFalse(
                GraphStageGate.lotsReady(
                        facts(building(Map.of()), terrain(Map.of(), Map.of("minecraft:dirt", 3)), List.of())),
                "яма на лоте держит этап открытым"
                        + " Иначе тест lots_CutOrFill_NotReady перестанет ловить эту поломку. При смене lots_CutOrFill_NotReady проверь это утверждение.");
    }

    @Test
    void strays_UnknownBuilder_Block() {
        GraphFacts facts = facts(
                building(Map.of()),
                GraphSiteFacts.Terrain.empty(),
                List.of(new GraphSiteFacts.Stray("s", "oak_fence", new GraphFacts.Position(1, 64, 1), null)));

        assertFalse(
                GraphStageGate.straysReady(facts),
                "старый мусор без записи журнала держит этап: блоки игрока в лишние не попадают"
                        + " Иначе тест strays_UnknownBuilder_Block перестанет ловить эту поломку. При смене strays_UnknownBuilder_Block проверь это утверждение.");
    }

    @Test
    void strays_SpiritBuilt_Block() {
        GraphFacts facts = facts(
                building(Map.of()),
                GraphSiteFacts.Terrain.empty(),
                List.of(new GraphSiteFacts.Stray("s", "oak_fence", new GraphFacts.Position(1, 64, 1), "spirit-1")));

        assertFalse(
                GraphStageGate.straysReady(facts),
                "лишняя постройка духа должна исчезнуть до закрытия этапа"
                        + " Иначе тест strays_SpiritBuilt_Block перестанет ловить эту поломку. При смене strays_SpiritBuilt_Block проверь это утверждение.");
    }

    private static GraphFacts.Building building(Map<String, Integer> missing) {
        int count = missing.values().stream().mapToInt(Integer::intValue).sum();
        return new GraphFacts.Building("hut", 1, count == 0 ? 100 : 90, count, missing, Map.of(), 0, null, false, true);
    }

    private static GraphSiteFacts.Terrain terrain(Map<String, Integer> cut, Map<String, Integer> fill) {
        return new GraphSiteFacts.Terrain(0, 0, 0, 0, cut, fill, Map.of(), false);
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
                new GraphSiteFacts.StageReqs(Map.of("hut", 1), Map.of()),
                GraphFeedback.none(),
                List.of(),
                List.of(),
                List.of());
    }
}
