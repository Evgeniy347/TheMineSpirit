package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorldFragmentTests {
    private static final Set<String> CLEANUP = Set.of(
            GraphWorldEvents.DEMOLISH_STRAY,
            GraphWorldEvents.BACKFILL,
            GraphWorldEvents.CLEAR_FLOATING,
            GraphWorldEvents.LEVEL_LOT,
            GraphWorldEvents.RESTORE_TRACE);

    @Test
    void defaultGraph_CleanupNodes_Declared() {
        SupplyGraph graph = SupplyGraph.loadDefault();

        for (String node : CLEANUP) {
            GraphEvent event = graph.events().get(node);
            assertTrue(event != null, "Узел уборки '" + node + "' объявлен во фрагменте мира");
            assertEquals(1, event.tasks().size(), "У узла уборки одно задание: " + node);
            assertEquals(node, event.tasks().get(0).id(), "Задание называет узел: " + node);
            assertTrue(
                    event.tasks().get(0).base() >= 40 && event.tasks().get(0).base() <= 100,
                    "Стартовый вес уборки разумен: " + node + " = "
                            + event.tasks().get(0).base());
        }
    }

    @Test
    void defaultGraph_StrayDemolition_ReturnsBlocks() {
        SupplyGraph graph = SupplyGraph.loadDefault();
        GraphEvent event = graph.events().get(GraphWorldEvents.DEMOLISH_STRAY);

        assertEquals(5, event.push().get("#building_blocks"), "Снос лишнего возвращает материал в оборот");
        assertTrue(graph.groups().containsKey("#building_blocks"), "Группа стройматериала объявлена фрагментом");
    }

    @Test
    void defaultGraph_RepairNodes_CoverStageBuildings() {
        SupplyGraph graph = SupplyGraph.loadDefault();

        for (String building : StageBuilt.ids()) {
            GraphEvent event = graph.events().get(GraphWorldEvents.REPAIR + building);
            assertTrue(event != null, "Ремонт постройки '" + building + "' объявлен узлом");
            GraphEvent.Task task = event.tasks().get(0);
            String expected = "chests".equals(building) ? "repair:stock_chests" : "repair:" + building;
            assertEquals(expected, task.id(), "Задание ремонта называет билет StageRepair для этой постройки");
            assertTrue(
                    task.base() >= 50 && task.base() <= 100,
                    "Стартовый вес ремонта разумен: " + building + " = " + task.base());
        }
    }

    @Test
    void planBuilding_Damaged_GetsItsOwnRepairTicket() {
        SupplyGraph graph = SupplyGraph.loadDefault();
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of("alchemy", PlanCaseFacts.building("alchemy", 60, 40, Map.of("oak_planks", 40), false)),
                List.of(),
                GraphSiteFacts.Terrain.empty());

        GraphPlan plan = PlanCaseFacts.plan(GraphWorld.settled(), facts);
        GraphPlan.Task repair = PlanCaseFacts.task(plan, "event:repair_alchemy:repair:alchemy");

        assertTrue(
                graph.events().containsKey("repair_alchemy"),
                "Повреждённая постройка плана без своего события получает repair_<id> из общего шаблона");
        assertTrue(
                repair.feasible(), "Аудит мира говорит «повреждена» — ремонт обязан быть выполнимым: " + plan.tasks());
        assertTrue(
                repair.priority() > 0,
                "Ремонт повреждённой постройки не нулевой: иначе доска его не выдаст (observe25 B9)");
    }

    @Test
    void planBuilding_Damaged_PostsRepairTicketToBoard() {
        GraphWorld world = GraphWorld.settled();
        world.built("weaver", GraphState.Built.BUILT);
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of("weaver", PlanCaseFacts.building("weaver", 60, 40, Map.of("oak_planks", 40), false)),
                List.of(),
                GraphSiteFacts.Terrain.empty());
        GraphState state = world.state().withFacts(facts, world.graph.costs().life());

        GraphTickets.Result result = GraphTickets.of(world.graph, state, GraphPlanner.plan(world.graph, state));

        assertTrue(
                result.post().stream()
                        .anyMatch(ticket -> "graph:event:repair_weaver:repair:weaver".equals(ticket.id())),
                "Повреждённая постройка плана (weaver) стоит в журнале BUILT, но аудит мира видит изъян — "
                        + "она обязана получить билет ремонта на доске, иначе два аудита расходятся (observe25 B9): "
                        + result.post().stream().map(GraphTickets.Ticket::id).toList());
    }

    @Test
    void clearFloating_HangingWorld_PostsTicketToCleanupAction() {
        SupplyGraph graph = SupplyGraph.loadDefault();
        GraphState state = new GraphState(
                Map.of(),
                1,
                Map.of("spirit-1", Map.of()),
                Map.of(),
                Map.of(),
                60,
                Set.of(GraphWorldEvents.CLEAR_FLOATING),
                Set.of());

        GraphTickets.Result result = GraphTickets.of(graph, state, GraphPlanner.plan(graph, state));
        GraphTickets.Ticket ticket = result.post().stream()
                .filter(found -> GraphWorldEvents.CLEAR_FLOATING.equals(found.node()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Висящее не превратилось в задание уборки: "
                        + result.post().stream().map(GraphTickets.Ticket::id).toList()));

        assertEquals(
                BoardCase.CLEAR_GROUNDS,
                ticket.task().boardCase(),
                "Задание clear_floating исполняется кейсом расчистки: иначе оно не находит исполнителя");
        assertTrue(
                ticket.id().startsWith("graph:event:clear_floating:"),
                "Билет уборки висящего — событие графа clear_floating: " + ticket.id());
    }

    @Test
    void repairTask_BuiltHouse_IsFeasible() {
        SupplyGraph graph = SupplyGraph.loadDefault();
        GraphState absent = state(GraphState.Built.ABSENT);
        GraphState built = state(GraphState.Built.BUILT);

        assertTrue(!task(graph, absent, GraphWorldEvents.REPAIR + "hut").feasible(), "Ремонт без постройки невыполним");
        assertTrue(
                task(graph, built, GraphWorldEvents.REPAIR + "hut").feasible(),
                "Ремонт построенного дома выполним, когда материалы есть");
    }

    private static GraphState state(GraphState.Built hut) {
        return new GraphState(
                Map.of("oak_planks", 64, "cobblestone", 64),
                1,
                Map.of("spirit-1", Map.of()),
                Map.of("hut", hut),
                Map.of(),
                60,
                Set.of(GraphWorldEvents.REPAIR + "hut"),
                Set.of());
    }

    private static GraphPlan.Task task(SupplyGraph graph, GraphState state, String event) {
        return GraphPlanner.plan(graph, state).tasks().stream()
                .filter(task -> task.id().startsWith("event:" + event + ":"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Нет задания события " + event + ": "
                        + GraphPlanner.plan(graph, state).tasks().stream()
                                .map(GraphPlan.Task::id)
                                .toList()));
    }
}
