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

        assertEquals(
                Set.of(),
                GraphWorldEvents.of(facts),
                "Ровный участок без следов не ставит уборку"
                        + " Иначе тест facts_FlatAndClean_GiveNoWorldEvents перестанет ловить эту поломку. При смене facts_FlatAndClean_GiveNoWorldEvents проверь это утверждение.");
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

        assertTrue(
                events.contains(GraphWorldEvents.DEMOLISH_STRAY),
                "Лишняя постройка требует сноса"
                        + " Иначе тест facts_StraysTerrainAndDamage_GiveNodes перестанет ловить эту поломку. При смене facts_StraysTerrainAndDamage_GiveNodes проверь это утверждение.");
        assertTrue(
                events.contains(GraphWorldEvents.BACKFILL),
                "Яма требует засыпки"
                        + " Иначе тест facts_StraysTerrainAndDamage_GiveNodes перестанет ловить эту поломку. При смене facts_StraysTerrainAndDamage_GiveNodes проверь это утверждение.");
        assertTrue(
                events.contains(GraphWorldEvents.CLEAR_FLOATING),
                "Висящее требует уборки"
                        + " Иначе тест facts_StraysTerrainAndDamage_GiveNodes перестанет ловить эту поломку. При смене facts_StraysTerrainAndDamage_GiveNodes проверь это утверждение.");
        assertTrue(
                events.contains(GraphWorldEvents.LEVEL_LOT),
                "Перепад требует выравнивания"
                        + " Иначе тест facts_StraysTerrainAndDamage_GiveNodes перестанет ловить эту поломку. При смене facts_StraysTerrainAndDamage_GiveNodes проверь это утверждение.");
        assertTrue(
                events.contains(GraphWorldEvents.RESTORE_TRACE),
                "Следы требуют возврата как было"
                        + " Иначе тест facts_StraysTerrainAndDamage_GiveNodes перестанет ловить эту поломку. При смене facts_StraysTerrainAndDamage_GiveNodes проверь это утверждение.");
        assertTrue(
                events.contains("repair_hut"),
                "Повреждение постройки требует ремонта"
                        + " Иначе тест facts_StraysTerrainAndDamage_GiveNodes перестанет ловить эту поломку. При смене facts_StraysTerrainAndDamage_GiveNodes проверь это утверждение.");
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

        assertFalse(
                events.contains("repair_hut"),
                "Неизвестный аудит не ставит ремонт"
                        + " Иначе тест facts_UnknownOrUndamagedBuilding_GiveNoRepair перестанет ловить эту поломку. При смене facts_UnknownOrUndamagedBuilding_GiveNoRepair проверь это утверждение.");
        assertFalse(
                events.contains("repair_mine"),
                "Целая постройка не ставит ремонт"
                        + " Иначе тест facts_UnknownOrUndamagedBuilding_GiveNoRepair перестанет ловить эту поломку. При смене facts_UnknownOrUndamagedBuilding_GiveNoRepair проверь это утверждение.");
    }

    @Test
    void facts_OldStrayWithoutBuilder_GivesDemolition() {
        GraphFacts facts = facts(
                List.of(new GraphSiteFacts.Stray("s", "oak_fence", new GraphFacts.Position(1, 64, 1), null)),
                GraphSiteFacts.Terrain.empty(),
                Map.of());

        assertTrue(
                GraphWorldEvents.of(facts).contains(GraphWorldEvents.DEMOLISH_STRAY),
                "старый мусор без записи журнала — всё равно лишнее: его сносит граф, а не журнал"
                        + " Иначе тест facts_OldStrayWithoutBuilder_GivesDemolition перестанет ловить эту поломку. При смене facts_OldStrayWithoutBuilder_GivesDemolition проверь это утверждение.");
    }

    @Test
    void facts_LotAndFloatingComposition_GiveNodes() {
        GraphFacts facts = facts(
                List.of(),
                new GraphSiteFacts.Terrain(
                        0, 0, 0, 0, 0, Map.of("minecraft:dirt", 2), Map.of(), Map.of("minecraft:oak_log", 4), false),
                Map.of());

        Set<String> events = GraphWorldEvents.of(facts);

        assertTrue(
                events.contains(GraphWorldEvents.LEVEL_LOT),
                "бугор из блоков требует срезки"
                        + " Иначе тест facts_LotAndFloatingComposition_GiveNodes перестанет ловить эту поломку. При смене facts_LotAndFloatingComposition_GiveNodes проверь это утверждение.");
        assertTrue(
                events.contains(GraphWorldEvents.CLEAR_FLOATING),
                "состав висящего требует уборки"
                        + " Иначе тест facts_LotAndFloatingComposition_GiveNodes перестанет ловить эту поломку. При смене facts_LotAndFloatingComposition_GiveNodes проверь это утверждение.");
    }

    @Test
    void facts_NaturalSlopeOnly_GivesNoLevelLot() {
        GraphFacts facts = facts(
                List.of(),
                new GraphSiteFacts.Terrain(0, 0, 0, 0, 0, Map.of(), Map.of(), Map.of(), false, 3263),
                Map.of());

        Set<String> events = GraphWorldEvents.of(facts);

        assertFalse(
                events.contains(GraphWorldEvents.LEVEL_LOT),
                "природный склон вне лотов — не работа выравнивания"
                        + " Иначе тест facts_NaturalSlopeOnly_GivesNoLevelLot перестанет ловить эту поломку. При смене facts_NaturalSlopeOnly_GivesNoLevelLot проверь это утверждение.");
        assertFalse(
                events.contains(GraphWorldEvents.BACKFILL),
                "природная впадина — не яма для засыпки"
                        + " Иначе тест facts_NaturalSlopeOnly_GivesNoLevelLot перестанет ловить эту поломку. При смене facts_NaturalSlopeOnly_GivesNoLevelLot проверь это утверждение.");
    }

    @Test
    void facts_FillComposition_GivesBackfill() {
        GraphFacts facts = facts(
                List.of(),
                new GraphSiteFacts.Terrain(0, 0, 0, 0, 0, Map.of(), Map.of("minecraft:dirt", 3), Map.of(), false),
                Map.of());

        assertTrue(
                GraphWorldEvents.of(facts).contains(GraphWorldEvents.BACKFILL),
                "яма из блоков требует подсыпки"
                        + " Иначе тест facts_FillComposition_GivesBackfill перестанет ловить эту поломку. При смене facts_FillComposition_GivesBackfill проверь это утверждение.");
    }

    @Test
    void state_WithWorldFacts_MergesDerivedEvents() {
        GraphState state = new GraphState(
                        Map.of(), 1, Map.of("spirit-1", Map.of()), Map.of(), Map.of(), 60, Set.of("night"), Set.of())
                .withFacts(
                        facts(List.of(), new GraphSiteFacts.Terrain(1, 0, 0, 2, 0), Map.of()),
                        GraphWorld.GRAPH.costs().life());

        assertTrue(
                state.events().contains("night"),
                "Старое событие не теряется"
                        + " Иначе тест state_WithWorldFacts_MergesDerivedEvents перестанет ловить эту поломку. При смене state_WithWorldFacts_MergesDerivedEvents проверь это утверждение.");
        assertTrue(
                state.events().contains(GraphWorldEvents.BACKFILL),
                "Вход графа добавляет узел засыпки"
                        + " Иначе тест state_WithWorldFacts_MergesDerivedEvents перестанет ловить эту поломку. При смене state_WithWorldFacts_MergesDerivedEvents проверь это утверждение.");
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
