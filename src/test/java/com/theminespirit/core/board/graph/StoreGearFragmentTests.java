package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoreGearFragmentTests {
    private static final List<String> STORE = List.of(
            StoreInputs.MINIMUM,
            StoreInputs.TRANSFER,
            StoreInputs.SMELT_PUT,
            StoreInputs.SMELT_TAKE,
            StoreInputs.STORAGE_SPACE,
            StoreInputs.SORT);
    private static final List<String> GEAR = List.of(
            GearInputs.EQUIP_BEST,
            GearInputs.CRAFT_ARMOR,
            GearInputs.CRAFT_TOOL + "pickaxe",
            GearInputs.CRAFT_TOOL + "axe",
            GearInputs.CRAFT_TOOL + "shovel",
            GearInputs.CRAFT_TOOL + "sword",
            GearInputs.CRAFT_TOOL + "hoe");

    @Test
    void defaultGraph_StoreNodes_Declared() {
        SupplyGraph graph = SupplyGraph.loadDefault();

        for (String node : STORE) {
            GraphEvent event = graph.events().get(node);
            assertTrue(event != null, "Складской узел '" + node + "' объявлен фрагментом store.json");
            assertFalse(event.tasks().isEmpty(), "Складской узел '" + node + "' несёт задание-заглушку");
            GraphEvent.Task task = event.tasks().get(0);
            assertTrue(task.base() >= 30 && task.base() <= 100, "Стартовый вес узла разумен: " + node);
            assertFalse(task.implemented(), "Складской узел '" + node + "' пока заглушка без исполнителя");
        }
    }

    @Test
    void defaultGraph_GearNodes_Declared() {
        SupplyGraph graph = SupplyGraph.loadDefault();

        for (String node : GEAR) {
            GraphEvent event = graph.events().get(node);
            assertTrue(event != null, "Узел снаряжения '" + node + "' объявлен фрагментом gear.json");
            assertFalse(event.tasks().isEmpty(), "Узел снаряжения '" + node + "' несёт задание-заглушку");
            assertFalse(event.tasks().get(0).implemented(), "Узел снаряжения '" + node + "' пока заглушка");
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
    void planner_StoreMinimumDeficit_RaisesGatherTask() {
        GraphFacts facts = facts(new GraphFacts.StageReqs(Map.of("cobblestone", 160), Map.of("cobblestone", 100)));
        GraphState state = GraphWorld.empty()
                .tools("spirit-1", 1)
                .with("cobblestone", 100)
                .state()
                .withFacts(facts);

        GraphPlan plan = GraphPlanner.plan(GraphWorld.GRAPH, state);

        assertTrue(
                plan.task("mine_cobble").isPresent(),
                "Недобор минимума склада обязан поднять добычу камня даже выше обычного порога");
        assertTrue(plan.priority("mine_cobble") > 0, "Задание добычи камня имеет положительный приоритет");
    }

    @Test
    void planner_ToolMinimum_RequestsIronPickaxe() {
        GraphFacts facts = facts(new GraphFacts.StageReqs(Map.of("iron_pickaxe", 1), Map.of("iron_pickaxe", 0)));
        GraphState state = GraphWorld.empty().tools("spirit-1", 1).state().withFacts(facts);

        GraphPlan plan = GraphPlanner.plan(GraphWorld.GRAPH, state);

        assertTrue(
                plan.tasks().stream().anyMatch(task -> task.id().startsWith("craft_iron_pickaxe@")),
                "Минимум железной кирки обязан связать нехватку с путём крафта: " + taskIds(plan));
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
        assertFalse(stub.implemented(), "Крафт кирки из входов снаряжения пока заглушка с TODO");
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

    private static GraphFacts facts(GraphFacts.StageReqs reqs) {
        return facts(reqs, Map.of());
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
