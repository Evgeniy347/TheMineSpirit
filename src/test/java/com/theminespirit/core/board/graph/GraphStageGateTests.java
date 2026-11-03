package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import org.junit.jupiter.api.Test;

class GraphStageGateTests {
    @Test
    void simulate_StageTwoBaseline_ReachesStageThree() {
        GraphSimulation.Outcome outcome =
                GraphSimulation.run(GraphMutations.baselines().get(3), 4000);

        assertTrue(outcome.reachedStage() >= 3, "Этап 2 обязан закрываться без сторожей: " + outcome.stuck());
        assertEquals(3, outcome.reachedStage(), "Мир этапа 2 доходит до этапа 3");
    }

    @Test
    void unbuilt_WatcherOwnedBuildings_Excluded() {
        GraphWorld world = GraphWorld.settled();
        world.stage = 2;
        GraphWorld.GRAPH.buildings().keySet().forEach(id -> world.buildings.put(id, Built.PLANNED));

        assertFalse(
                GraphSimulation.unbuilt(world).contains("warehouse"),
                "Склад строит сторож, а не граф: он не держит этап");
        assertFalse(
                GraphSimulation.unbuilt(world).contains("pen_cow"),
                "Загон строит сторож, а не граф: он не держит этап");
    }
}
