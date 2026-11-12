package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphClearingAuditTests {
    @Test
    void plan_ClearingPartlyAudited_StageClearingPostedNotLeftToRepair() {
        GraphWorld world = GraphWorld.settled().built("clearing", Built.PLANNED).with("oak_planks", 32);
        GraphFacts facts = new GraphFacts(
                named(spirit("spirit-1")),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(
                        "clearing",
                        new GraphFacts.Building("clearing", 1, 60, 0, Map.of(), Map.of(), 0, null, false, true)),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()),
                GraphFeedback.none(),
                List.of(),
                List.of(),
                List.of());

        GraphPlan result = plan(world, facts);

        assertTrue(
                result.raised("building:clearing"),
                "Участок не ровный, аудит видит часть — граф ставит расчистку этапа (слоями): ремонт по схеме для "
                        + "рельефа не возникает, иначе расчистка стоит: " + CrossScene.ranked(result));
    }
}
