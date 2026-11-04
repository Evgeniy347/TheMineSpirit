package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GearFragmentTests {
    private static final List<String> GEAR = List.of(
            GearInputs.EQUIP_BEST,
            GearInputs.CRAFT_ARMOR,
            GearInputs.CRAFT_TOOL + "pickaxe",
            GearInputs.CRAFT_TOOL + "axe",
            GearInputs.CRAFT_TOOL + "shovel",
            GearInputs.CRAFT_TOOL + "sword",
            GearInputs.CRAFT_TOOL + "hoe");

    @Test
    void defaultGraph_GearNodes_Declared() {
        SupplyGraph graph = SupplyGraph.loadDefault();

        for (String node : GEAR) {
            GraphEvent event = graph.events().get(node);
            assertTrue(event != null, "Узел снаряжения '" + node + "' объявлен фрагментом gear.json");
            assertFalse(event.tasks().isEmpty(), "Узел снаряжения '" + node + "' несёт задание-заглушку");
            assertTrue(event.tasks().get(0).implemented(), "Узел снаряжения '" + node + "' исполняется действием");
        }
        assertEquals(
                "equip_best",
                graph.events().get(GearInputs.EQUIP_BEST).tasks().get(0).id(),
                "Задание экипировки называет узел");
        assertEquals(
                "craft_tool:pickaxe",
                graph.events()
                        .get(GearInputs.CRAFT_TOOL + "pickaxe")
                        .tasks()
                        .get(0)
                        .id(),
                "Задание кирки называет вид инструмента");
    }

    @Test
    void defaultGraph_GearKitPush_NamesKnownNodes() {
        SupplyGraph graph = SupplyGraph.loadDefault();

        for (String node : graph.events().keySet()) {
            if (!node.startsWith(GearInputs.KIT)) {
                continue;
            }
            for (String pushed : graph.events().get(node).push().keySet()) {
                assertTrue(known(graph, pushed), "Набор '" + node + "' подталкивает неизвестный узел " + pushed);
            }
        }
    }

    @Test
    void planner_GearInputs_AddUnimplementedStubs() {
        GraphFacts facts = facts(
                new GraphFacts.StageReqs(Map.of(), Map.of()),
                Map.of(
                        "spirit-1",
                        new GraphFacts.Spirit(
                                "spirit-1",
                                new GraphFacts.Position(0, 64, 0),
                                20,
                                18,
                                300,
                                false,
                                false,
                                false,
                                null,
                                Map.of(),
                                List.of(),
                                List.of())));
        GraphState state = GraphWorld.empty().state().withFacts(facts);

        GraphPlan plan = GraphPlanner.plan(GraphWorld.GRAPH, state);

        GraphPlan.Task stub = plan.task("event:gear_craft_pickaxe:craft_tool:pickaxe")
                .orElseThrow(() -> new AssertionError("Нет задания запаса кирки: " + taskIds(plan)));
        assertTrue(stub.implemented(), "Крафт кирки из входов снаряжения исполняется действием");
    }

    private static List<String> taskIds(GraphPlan plan) {
        return plan.tasks().stream().map(GraphPlan.Task::id).sorted().toList();
    }

    private static boolean known(SupplyGraph graph, String node) {
        if (node.startsWith("building:")) {
            return graph.buildings().containsKey(node.substring("building:".length()));
        }
        if (node.startsWith("tool:")) {
            String kind = node.substring("tool:".length());
            return graph.kit().kinds().contains(kind);
        }
        return graph.items().containsKey(node) || graph.isGroup(node) || graph.isTool(node);
    }

    private static GraphFacts facts(GraphFacts.StageReqs reqs, Map<String, GraphFacts.Spirit> spirits) {
        return new GraphFacts(
                spirits,
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                new LinkedHashMap<>(),
                List.of(),
                GraphFacts.Terrain.empty(),
                reqs);
    }
}
