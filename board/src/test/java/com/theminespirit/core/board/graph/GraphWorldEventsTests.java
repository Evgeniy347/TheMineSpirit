package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphWorldEventsTests {
    @Test
    void facts_FlatAndClean_GiveNoWorldEvents() {
        GraphFacts facts = facts(List.of(), GraphSiteFacts.Terrain.empty(), Map.of());

        assertEquals(Set.of(), GraphWorldEvents.of(facts), "Ровный участок без следов не ставит уборку");
    }

    @Test
    void facts_StraysTerrainAndDamage_GiveNodes() {
        GraphFacts facts = facts(
                List.of(new GraphSiteFacts.Stray("s", "wall", new GraphFacts.Position(1, 64, 1), "spirit-1")),
                new GraphSiteFacts.Terrain(2, 3, 4, 5, 6),
                Map.of(
                        "hut",
                        new GraphFacts.Building(
                                "hut", 1, 75, 5, Map.of("minecraft:dirt", 5), Map.of(), 0, null, false, true)));

        Set<String> events = GraphWorldEvents.of(facts);

        assertTrue(events.contains(GraphWorldEvents.DEMOLISH_STRAY), "Лишняя постройка требует сноса");
        assertTrue(events.contains(GraphWorldEvents.BACKFILL), "Яма требует засыпки");
        assertTrue(events.contains(GraphWorldEvents.CLEAR_FLOATING), "Висящее требует уборки");
        assertTrue(events.contains(GraphWorldEvents.LEVEL_LOT), "Перепад требует выравнивания");
        assertTrue(events.contains(GraphWorldEvents.RESTORE_TRACE), "Следы требуют возврата как было");
        assertTrue(events.contains("repair_hut"), "Повреждение постройки требует ремонта");
    }

    @Test
    void facts_UnknownOrUndamagedBuilding_GiveNoRepair() {
        GraphFacts facts = facts(
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                Map.of(
                        "hut",
                                new GraphFacts.Building(
                                        "hut", 1, 0, 5, Map.of("minecraft:dirt", 5), Map.of(), 0, null, true, true),
                        "mine", new GraphFacts.Building("mine", 1, 100, 0, Map.of(), Map.of(), 0, null, false, true)));

        Set<String> events = GraphWorldEvents.of(facts);

        assertFalse(events.contains("repair_hut"), "Неизвестный аудит не ставит ремонт");
        assertFalse(events.contains("repair_mine"), "Целая постройка не ставит ремонт");
    }

    @Test
    void facts_OldStrayWithoutBuilder_GivesDemolition() {
        GraphFacts facts = facts(
                List.of(new GraphSiteFacts.Stray("s", "oak_fence", new GraphFacts.Position(1, 64, 1), null)),
                GraphSiteFacts.Terrain.empty(),
                Map.of());

        assertTrue(
                GraphWorldEvents.of(facts).contains(GraphWorldEvents.DEMOLISH_STRAY),
                "старый мусор без записи журнала — всё равно лишнее: его сносит граф, а не журнал");
    }

    @Test
    void facts_LotAndFloatingComposition_GiveNodes() {
        GraphFacts facts = facts(
                List.of(),
                new GraphSiteFacts.Terrain(
                        0, 0, 0, 0, 0, Map.of("minecraft:dirt", 2), Map.of(), Map.of("minecraft:oak_log", 4), false),
                Map.of());

        Set<String> events = GraphWorldEvents.of(facts);

        assertTrue(events.contains(GraphWorldEvents.LEVEL_LOT), "бугор из блоков требует срезки");
        assertTrue(events.contains(GraphWorldEvents.CLEAR_FLOATING), "состав висящего требует уборки");
    }

    @Test
    void facts_NaturalSlopeOnly_GivesNoLevelLot() {
        GraphFacts facts = facts(
                List.of(),
                new GraphSiteFacts.Terrain(0, 0, 0, 0, 0, Map.of(), Map.of(), Map.of(), false, 3263),
                Map.of());

        Set<String> events = GraphWorldEvents.of(facts);

        assertFalse(events.contains(GraphWorldEvents.LEVEL_LOT), "природный склон вне лотов — не работа выравнивания");
        assertFalse(events.contains(GraphWorldEvents.BACKFILL), "природная впадина — не яма для засыпки");
    }

    @Test
    void facts_FillComposition_GivesBackfill() {
        GraphFacts facts = facts(
                List.of(),
                new GraphSiteFacts.Terrain(0, 0, 0, 0, 0, Map.of(), Map.of("minecraft:dirt", 3), Map.of(), false),
                Map.of());

        assertTrue(GraphWorldEvents.of(facts).contains(GraphWorldEvents.BACKFILL), "яма из блоков требует подсыпки");
    }

    @Test
    void state_WithWorldFacts_MergesDerivedEvents() {
        GraphState state = new GraphState(
                        Map.of(), 1, Map.of("spirit-1", Map.of()), Map.of(), Map.of(), 60, Set.of("night"), Set.of())
                .withFacts(
                        facts(List.of(), new GraphSiteFacts.Terrain(1, 0, 0, 2, 0), Map.of()),
                        GraphWorld.GRAPH.costs().life());

        assertTrue(state.events().contains("night"), "Старое событие не теряется");
        assertTrue(state.events().contains(GraphWorldEvents.BACKFILL), "Вход графа добавляет узел засыпки");
    }

    private static GraphFacts facts(
            List<GraphSiteFacts.Stray> strays,
            GraphSiteFacts.Terrain terrain,
            Map<String, GraphFacts.Building> buildings) {
        return new GraphFacts(
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                buildings,
                strays,
                terrain,
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()),
                GraphFeedback.none(),
                List.of(),
                List.of(),
                List.of());
    }
}
