package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.ids;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageTwo;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StageLevelingSpiritsTests {
    @Test
    void leveling_DefectAfterClosedStage_ReturnsTwoSpiritsToWork() {
        GraphWorld world = stageTwo();
        world.with("dirt", 64);
        GraphFacts clean = facts(named(spirit("spirit-1"), spirit("spirit-2")));
        assertFalse(
                plan(world, clean).tasks().stream().anyMatch(task -> task.id().startsWith("event:level_lot")),
                "Ровная зона не держит духов: выравнивание поднимается только по дефекту");

        GraphFacts defect = facts(
                named(spirit("spirit-1"), spirit("spirit-2")),
                List.of(),
                List.of(),
                new GraphSiteFacts.Terrain(
                        1, 0, 3, 1, 0, Map.of("minecraft:dirt", 2), Map.of("minecraft:dirt", 2), Map.of()),
                Map.of());
        GraphPlan plan = plan(world, defect);

        assertTrue(
                task(plan, "event:level_lot:level_lot").feasible(),
                "Столб и яма после закрытия этапа снова дают работу выравнивания: " + ids(plan));
        assertTrue(
                plan.tasks().stream().anyMatch(task -> task.spirit().isPresent()),
                "Работа выравнивания раскладывается по духам графом, а не ставится мимо него: " + ids(plan));
    }
}
