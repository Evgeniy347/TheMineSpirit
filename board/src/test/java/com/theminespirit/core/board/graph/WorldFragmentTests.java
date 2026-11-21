package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
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
            assertTrue(
                    event != null,
                    "Узел уборки '" + node + "' объявлен во фрагменте мира"
                            + " Иначе тест defaultGraph_CleanupNodes_Declared перестанет ловить эту поломку. При смене defaultGraph_CleanupNodes_Declared проверь это утверждение.");
            assertEquals(
                    1,
                    event.tasks().size(),
                    "У узла уборки одно задание: " + node
                            + " Иначе тест defaultGraph_CleanupNodes_Declared перестанет ловить эту поломку. При смене defaultGraph_CleanupNodes_Declared проверь это утверждение.");
            assertEquals(
                    node,
                    event.tasks().get(0).id(),
                    "Задание называет узел: " + node
                            + " Иначе тест defaultGraph_CleanupNodes_Declared перестанет ловить эту поломку. При смене defaultGraph_CleanupNodes_Declared проверь это утверждение.");
            assertTrue(
                    event.tasks().get(0).base() >= 40 && event.tasks().get(0).base() <= 100,
                    "Стартовый вес уборки разумен: " + node + " = "
                            + event.tasks().get(0).base()
                            + " Иначе тест defaultGraph_CleanupNodes_Declared перестанет ловить эту поломку. При смене defaultGraph_CleanupNodes_Declared проверь это утверждение.");
        }
    }

    @Test
    void defaultGraph_StrayDemolition_ReturnsBlocks() {
        SupplyGraph graph = SupplyGraph.loadDefault();
        GraphEvent event = graph.events().get(GraphWorldEvents.DEMOLISH_STRAY);

        assertEquals(
                5,
                event.push().get("#building_blocks"),
                "Снос лишнего возвращает материал в оборот"
                        + " Иначе тест defaultGraph_StrayDemolition_ReturnsBlocks перестанет ловить эту поломку. При смене defaultGraph_StrayDemolition_ReturnsBlocks проверь это утверждение.");
        assertTrue(
                graph.groups().containsKey("#building_blocks"),
                "Группа стройматериала объявлена фрагментом"
                        + " Иначе тест defaultGraph_StrayDemolition_ReturnsBlocks перестанет ловить эту поломку. При смене defaultGraph_StrayDemolition_ReturnsBlocks проверь это утверждение.");
    }

    @Test
    void defaultGraph_RepairNodes_CoverStageBuildings() {
        SupplyGraph graph = SupplyGraph.loadDefault();

        for (String building : StageBuilt.ids()) {
            GraphEvent event = graph.events().get(GraphWorldEvents.REPAIR + building);
            assertTrue(
                    event != null,
                    "Ремонт постройки '" + building + "' объявлен узлом"
                            + " Иначе тест defaultGraph_RepairNodes_CoverStageBuildings перестанет ловить эту поломку. При смене defaultGraph_RepairNodes_CoverStageBuildings проверь это утверждение.");
            GraphEvent.Task task = event.tasks().get(0);
            String expected = "chests".equals(building) ? "repair:stock_chests" : "repair:" + building;
            assertEquals(
                    expected,
                    task.id(),
                    "Задание ремонта называет билет StageRepair для этой постройки"
                            + " Иначе тест defaultGraph_RepairNodes_CoverStageBuildings перестанет ловить эту поломку. При смене defaultGraph_RepairNodes_CoverStageBuildings проверь это утверждение.");
            assertTrue(
                    task.base() >= 50 && task.base() <= 100,
                    "Стартовый вес ремонта разумен: " + building + " = " + task.base()
                            + " Иначе тест defaultGraph_RepairNodes_CoverStageBuildings перестанет ловить эту поломку. При смене defaultGraph_RepairNodes_CoverStageBuildings проверь это утверждение.");
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
                "Повреждённая постройка плана без своего события получает repair_<id> из общего шаблона"
                        + " Иначе тест planBuilding_Damaged_GetsItsOwnRepairTicket перестанет ловить эту поломку. При смене planBuilding_Damaged_GetsItsOwnRepairTicket проверь это утверждение.");
        assertTrue(
                repair.feasible(),
                "Аудит мира говорит «повреждена» — ремонт обязан быть выполнимым: " + plan.tasks()
                        + " Иначе тест planBuilding_Damaged_GetsItsOwnRepairTicket перестанет ловить эту поломку. При смене planBuilding_Damaged_GetsItsOwnRepairTicket проверь это утверждение.");
        assertTrue(
                repair.raised(),
                "Ремонт повреждённой постройки не нулевой: иначе доска его не выдаст (observe25 B9)"
                        + " При смене planBuilding_Damaged_GetsItsOwnRepairTicket проверь это утверждение.");
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
                        + result.post().stream().map(GraphTickets.Ticket::id).toList()
                        + " При смене planBuilding_Damaged_PostsRepairTicketToBoard проверь это утверждение.");
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
                "Задание clear_floating исполняется кейсом расчистки: иначе оно не находит исполнителя"
                        + " При смене clearFloating_HangingWorld_PostsTicketToCleanupAction проверь это утверждение.");
        assertTrue(
                ticket.id().startsWith("graph:event:clear_floating:"),
                "Билет уборки висящего — событие графа clear_floating: " + ticket.id()
                        + " Иначе тест clearFloating_HangingWorld_PostsTicketToCleanupAction перестанет ловить эту поломку. При смене clearFloating_HangingWorld_PostsTicketToCleanupAction проверь это утверждение.");
    }

    @Test
    void repairTask_BuiltHouse_IsFeasible() {
        SupplyGraph graph = SupplyGraph.loadDefault();
        GraphState absent = state(GraphState.Built.ABSENT);
        GraphState built = state(GraphState.Built.BUILT);

        assertTrue(
                !task(graph, absent, GraphWorldEvents.REPAIR + "hut").feasible(),
                "Ремонт без постройки невыполним"
                        + " Иначе тест repairTask_BuiltHouse_IsFeasible перестанет ловить эту поломку. При смене repairTask_BuiltHouse_IsFeasible проверь это утверждение.");
        assertTrue(
                task(graph, built, GraphWorldEvents.REPAIR + "hut").feasible(),
                "Ремонт построенного дома выполним, когда материалы есть"
                        + " Иначе тест repairTask_BuiltHouse_IsFeasible перестанет ловить эту поломку. При смене repairTask_BuiltHouse_IsFeasible проверь это утверждение.");
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
