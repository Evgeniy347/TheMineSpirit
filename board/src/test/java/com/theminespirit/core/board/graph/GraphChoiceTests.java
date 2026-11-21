package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.inventory.api.JsonResource;
import org.junit.jupiter.api.Test;

class GraphChoiceTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void choice_DefaultGraph_IsNeutral() {
        assertEquals(
                GraphChoice.NEUTRAL,
                GRAPH.costs().choice(),
                "Веса по умолчанию нейтральны: поведение графа не меняется Иначе тест choice_DefaultGraph_IsNeutral перестанет ловить эту поломку. При смене choice_DefaultGraph_IsNeutral проверь это утверждение.");
    }

    @Test
    void choice_NegativeParameter_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> tuned("{\"farTrip\": -1}"),
                "Отрицательная поправка ломает сравнение весов: её отвергают с объяснением При смене choice_NegativeParameter_IsRejected проверь это утверждение.");
    }

    private static SupplyGraph tuned(String json) {
        GraphChoice choice = GraphChoice.parse(JsonResource.parse(json, "test:choice"));
        GraphCosts costs = GRAPH.costs();
        GraphCosts tuned = new GraphCosts(
                costs.defaultTicks(),
                costs.caseTicks(),
                costs.wayTicks(),
                costs.wayUses(),
                costs.ticksPerBlock(),
                costs.travelScaleTicks(),
                costs.toolDurability(),
                costs.stationCapacity(),
                choice,
                costs.life(),
                costs.undo());
        return new SupplyGraph(
                GRAPH.decay(),
                GRAPH.urgentBoost(),
                GRAPH.buildReadyBoost(),
                GRAPH.maxPriority(),
                GRAPH.tiers(),
                GRAPH.kit(),
                GRAPH.storage(),
                GRAPH.items(),
                GRAPH.buildings(),
                GRAPH.ways(),
                GRAPH.edges(),
                GRAPH.unimplemented(),
                GRAPH.events(),
                GRAPH.groups(),
                GRAPH.triggers(),
                GRAPH.herdCaps(),
                GRAPH.buildStartShare(),
                tuned);
    }
}
