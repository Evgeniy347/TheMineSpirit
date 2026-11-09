package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StageLevelingScenarioTests {
    @Test
    void defectAfterClosure_AfterClosedStage_BlocksAndRaisesLeveling() {
        GraphFacts clean = facts(building(), GraphSiteFacts.Terrain.empty(), List.of());
        assertTrue(GraphStageGate.lotsReady(clean), "Ровная зона готова: этап можно закрывать");

        GraphFacts defect = facts(
                building(),
                new GraphSiteFacts.Terrain(
                        1, 0, 0, 1, 0, Map.of("minecraft:dirt", 1), Map.of("minecraft:dirt", 1), Map.of(), false),
                List.of());

        assertFalse(
                GraphStageGate.lotsReady(defect),
                "Столб или яма, появившиеся после закрытия, снова держат этап: ровность — условие перехода");
        assertTrue(
                GraphWorldEvents.of(defect).contains(GraphWorldEvents.LEVEL_LOT),
                "Дефект зоны поднимает узел выравнивания, а не задачу мимо графа: " + GraphWorldEvents.of(defect));
    }

    private static GraphFacts.Building building() {
        return new GraphFacts.Building("hut", 1, 100, 0, Map.of(), Map.of(), 0, null, false, true);
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
                List.of());
    }
}
