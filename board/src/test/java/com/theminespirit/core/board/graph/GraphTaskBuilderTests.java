package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphTaskBuilderTests {
    @Test
    void stationTask_BuiltStationWithoutPlanks_RequiresRecipeMaterials() {
        GraphWorld world = GraphWorld.settled().with("oak_planks", 0);
        GraphTaskBuilder builder = builder(world);

        GraphPlan.Task task =
                builder.stationTask("crafting_table", 10, busyScores()).orElseThrow();

        assertEquals(
                Map.of("#planks", 4),
                task.consumes(),
                "Дополнительный верстак требует четыре доски даже после постройки первого: иначе граф выдаёт "
                        + "бесплатную станцию, а дух падает без ингредиентов. При смене "
                        + "stationTask_BuiltStationWithoutPlanks_RequiresRecipeMaterials проверь GraphTaskBuilder."
                        + "stationTask и живой graph:station:crafting_table.");
        assertFalse(
                task.feasible(),
                "Без досок дополнительная станция невыполнима: иначе задание снова выдаётся свободному духу. "
                        + "При смене stationTask_BuiltStationWithoutPlanks_RequiresRecipeMaterials проверь "
                        + "GraphTickets.of и ResourceGate.");
    }

    @Test
    void stationTask_BuiltStationWithPlanks_IsFeasible() {
        GraphWorld world = GraphWorld.settled().with("oak_planks", 4);
        GraphTaskBuilder builder = builder(world);

        GraphPlan.Task task =
                builder.stationTask("crafting_table", 10, busyScores()).orElseThrow();

        assertTrue(
                task.feasible(),
                "Четыре доски делают дополнительный верстак выполнимым: иначе граф блокирует нормальную "
                        + "расширку станции. При смене stationTask_BuiltStationWithPlanks_IsFeasible проверь "
                        + "GraphTaskBuilder.stationTask и TransferWatch.");
    }

    private static GraphTaskBuilder builder(GraphWorld world) {
        GraphReach reach = new GraphReach(world.graph, world.state());
        return new GraphTaskBuilder(
                world.graph, world.state(), reach, new GraphChoiceRules(world.graph, world.state(), reach), Set.of());
    }

    private static Map<String, Double> busyScores() {
        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("craft_stone_pickaxe@spirit-1", 1.0);
        scores.put("craft_stone_axe@spirit-2", 1.0);
        scores.put("craft_stone_shovel@spirit-3", 1.0);
        scores.put("craft_stone_sword@spirit-4", 1.0);
        scores.put("craft_stone_hoe@spirit-5", 1.0);
        return scores;
    }
}
