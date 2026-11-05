package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphRepairPlannerTests {
    private static final String REPAIR = "event:repair_hut:repair:hut";

    @Test
    void repair_DamagedHut_ConsumesExactlyMissingItems() {
        GraphWorld world = GraphWorld.settled()
                .built("hut", Built.BUILT)
                .event("repair_hut")
                .facts(facts(75, Map.of("minecraft:dirt", 3)));
        world.materials.put("hut", Map.of("dirt", 3));

        GraphPlan plan = world.plan();

        GraphPlan.Task repair = task(plan);
        assertEquals(
                Map.of("dirt", 3),
                repair.consumes(),
                "ремонт хижины потребляет ровно недостающие предметы, а не фиксированный набор схемы");
    }

    @Test
    void repair_MoreMissing_RaisesWeight() {
        GraphWorld light = GraphWorld.settled()
                .built("hut", Built.BUILT)
                .event("repair_hut")
                .facts(facts(75, Map.of("minecraft:dirt", 3)));
        GraphWorld heavy = GraphWorld.settled()
                .built("hut", Built.BUILT)
                .event("repair_hut")
                .facts(facts(25, Map.of("minecraft:dirt", 15)));

        assertTrue(
                task(heavy.plan()).priority() > task(light.plan()).priority(),
                "чем больше доля недостающего, тем выше вес ремонта: "
                        + task(heavy.plan()).priority() + " против "
                        + task(light.plan()).priority());
    }

    @Test
    void repair_NotStartedBuilding_NotFeasible() {
        GraphWorld world = GraphWorld.settled()
                .with("dirt", 3)
                .built("hut", Built.PLANNED)
                .event("repair_hut")
                .facts(facts(0, Map.of("minecraft:dirt", 3)));
        world.materials.put("hut", Map.of("dirt", 3));

        assertTrue(
                !task(world.plan()).feasible(),
                "Постройка на 0% не ремонтируется: её строят с нуля, иначе задание ремонта подменяет стройку");
    }

    private static GraphPlan.Task task(GraphPlan plan) {
        List<GraphPlan.Task> repairs =
                plan.tasks().stream().filter(task -> REPAIR.equals(task.id())).toList();
        assertEquals(1, repairs.size(), "повреждённая постройка даёт одно задание ремонта: " + plan.tasks());
        return repairs.get(0);
    }

    private static GraphFacts facts(int percent, Map<String, Integer> missing) {
        return new GraphFacts(
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of("hut", new GraphFacts.Building("hut", 1, percent, 3, missing, Map.of(), 0, null, false)),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                new GraphSiteFacts.StageReqs(Map.of("hut", 1), Map.of()));
    }
}
