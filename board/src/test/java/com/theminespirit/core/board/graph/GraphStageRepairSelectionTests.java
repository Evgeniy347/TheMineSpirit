package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphStageRepairSelectionTests {
    private static final List<String> PENS = List.of("chicken_coop", "pen_cow", "pen_sheep", "pen_pig");

    @Test
    void stageTwo_Pens_AreChosenByGraphNotByWatcher() {
        GraphWorld world = GraphWorld.settled();
        world.stage = 2;
        world.with("oak_log", 200).with("oak_planks", 200).with("cobblestone", 200);
        for (String id : PENS) {
            world.built(id, Built.PLANNED);
        }

        GraphPlan plan = world.plan();
        String missing = PENS.stream()
                .filter(id -> plan.task("building:" + id).isEmpty())
                .reduce("", (left, id) -> left + id + " ");

        assertTrue(
                missing.isEmpty(),
                "Курятник и загоны этапа 2 ставит граф своими узлами building:<id>, а не сторож: нет " + missing + " в "
                        + plan.tasks());
    }
}
