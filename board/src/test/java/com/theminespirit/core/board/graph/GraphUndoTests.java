package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GraphUndoTests {
    private static final GraphUndo UNDO = GraphUndo.DEFAULTS;

    @Test
    void insideSettlement_ComparedToFarTrip_WeighsMore() {
        GraphSiteFacts.Lease inside = lease(5, true, true, 6, 4, null);
        GraphSiteFacts.Lease far = lease(0, false, false, 2, 300, null);

        assertTrue(
                UNDO.weight(inside, false, 100) > UNDO.weight(far, false, 100),
                "Столб на улице поселения важнее брошенного моста далеко в походе"
                        + " Иначе тест insideSettlement_ComparedToFarTrip_WeighsMore перестанет ловить эту поломку. При смене insideSettlement_ComparedToFarTrip_WeighsMore проверь это утверждение.");
    }

    @Test
    void streetHeightAndCells_RaiseWeight_DistanceLowers() {
        GraphSiteFacts.Lease plain = lease(0, false, false, 1, 300, null);
        GraphSiteFacts.Lease street = lease(0, true, true, 1, 300, null);
        GraphSiteFacts.Lease high = lease(6, false, false, 1, 300, null);
        GraphSiteFacts.Lease many = lease(0, false, false, 10, 300, null);
        GraphSiteFacts.Lease near = lease(0, false, false, 1, 0, null);

        assertTrue(
                UNDO.weight(street, false, 100) > UNDO.weight(plain, false, 100),
                "Правка на пути/улице мешает ходить — её вес выше"
                        + " Иначе тест streetHeightAndCells_RaiseWeight_DistanceLowers перестанет ловить эту поломку. При смене streetHeightAndCells_RaiseWeight_DistanceLowers проверь это утверждение.");
        assertTrue(
                UNDO.weight(high, false, 100) > UNDO.weight(plain, false, 100),
                "Висящая над землёй правка — риск падения, вес выше"
                        + " Иначе тест streetHeightAndCells_RaiseWeight_DistanceLowers перестанет ловить эту поломку. При смене streetHeightAndCells_RaiseWeight_DistanceLowers проверь это утверждение.");
        assertTrue(
                UNDO.weight(many, false, 100) > UNDO.weight(plain, false, 100),
                "Много клеток убирать дольше — вес выше"
                        + " Иначе тест streetHeightAndCells_RaiseWeight_DistanceLowers перестанет ловить эту поломку. При смене streetHeightAndCells_RaiseWeight_DistanceLowers проверь это утверждение.");
        assertTrue(
                UNDO.weight(near, false, 100) > UNDO.weight(plain, false, 100),
                "Далеко в походе уборка дешевле: дорога не окупается"
                        + " Иначе тест streetHeightAndCells_RaiseWeight_DistanceLowers перестанет ловить эту поломку. При смене streetHeightAndCells_RaiseWeight_DistanceLowers проверь это утверждение.");
    }

    @Test
    void ownerAlive_ComparedToUnknownOwner_MakesRollbackCheaper() {
        GraphSiteFacts.Lease lease = lease(4, true, true, 4, 4, "spirit-1");

        assertTrue(
                UNDO.weight(lease, true, 100) < UNDO.weight(lease, false, 100),
                "Владелец аренды рядом и знает свои клетки: его откат дешевле"
                        + " Иначе тест ownerAlive_ComparedToUnknownOwner_MakesRollbackCheaper перестанет ловить эту поломку. При смене ownerAlive_ComparedToUnknownOwner_MakesRollbackCheaper проверь это утверждение.");
    }

    @Test
    void weights_LoadedFromGraphJson_MatchDefaults() {
        SupplyGraph graph = SupplyGraph.loadDefault();

        assertEquals(
                GraphUndo.DEFAULTS.base(),
                graph.costs().undo().base(),
                "Базовый вес отката берётся из graph.json → undo, а не из кода"
                        + " Иначе тест weights_LoadedFromGraphJson_MatchDefaults перестанет ловить эту поломку. При смене weights_LoadedFromGraphJson_MatchDefaults проверь это утверждение.");
        assertTrue(
                graph.costs().undo().inside() > 0,
                "Поправка поселения задана данными"
                        + " Иначе тест weights_LoadedFromGraphJson_MatchDefaults перестанет ловить эту поломку. При смене weights_LoadedFromGraphJson_MatchDefaults проверь это утверждение.");
    }

    @Test
    void deepPocketOutsideSettlement_ComparedToDamageInside_WeighsLess() {
        GraphSiteFacts.Lease pocket = new GraphSiteFacts.Lease(
                "dig-1", "dig_out", null, new GraphFacts.Position(20, 58, 0), 0, false, false, 2, 20, 12);
        GraphSiteFacts.Lease inside = new GraphSiteFacts.Lease(
                "dig-2", "dig_out", null, new GraphFacts.Position(0, 58, 0), 0, true, false, 2, 4, 12);

        assertTrue(
                UNDO.weight(inside, false, 100) > UNDO.weight(pocket, false, 100),
                "Сломанное под поселением возвращают раньше кармана вне зоны: " + "под поселением "
                        + UNDO.weight(inside, false, 100) + " против кармана " + UNDO.weight(pocket, false, 100)
                        + " Иначе тест deepPocketOutsideSettlement_ComparedToDamageInside_WeighsLess перестанет ловить эту поломку. При смене deepPocketOutsideSettlement_ComparedToDamageInside_WeighsLess проверь это утверждение.");
    }

    private static GraphSiteFacts.Lease lease(
            int height, boolean inside, boolean street, int cells, int distance, String owner) {
        return new GraphSiteFacts.Lease(
                "lease-" + height + "-" + cells,
                "scaffold",
                owner,
                new GraphFacts.Position(0, 70 + height, 0),
                height,
                inside,
                street,
                cells,
                distance);
    }
}
