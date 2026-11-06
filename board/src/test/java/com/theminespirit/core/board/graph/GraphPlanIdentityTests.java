package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphPlanIdentityTests {
    @Test
    void plan_AnyBaseline_IdsAreUnique() {
        Set<String> duplicates = new HashSet<>();
        for (GraphWorld world : GraphMutations.baselines()) {
            GraphPlan plan = world.plan();
            Set<String> seen = new HashSet<>();
            for (GraphPlan.Task task : plan.tasks()) {
                if (!seen.add(task.id())) {
                    duplicates.add(task.id());
                }
            }
        }
        for (GraphWorld world : List.of(
                GraphWorld.settled(),
                GraphWorld.settled().with("coal", 0).with("oak_log", 0),
                GraphWorld.settled().with("oak_log", 0).built("orchard_oak", GraphState.Built.BUILT))) {
            GraphPlan plan = world.plan();
            Set<String> seen = new HashSet<>();
            for (GraphPlan.Task task : plan.tasks()) {
                if (!seen.add(task.id())) {
                    duplicates.add(task.id());
                }
            }
        }

        assertEquals(
                Set.of(),
                duplicates,
                "Два задания с одним id сливаются на доске в одно, и работа теряется: "
                        + "рубка и её часть считаются одним тикетом, а вторая часть никогда не выдаётся");
    }
}
