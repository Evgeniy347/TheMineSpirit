package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.RepairPlan;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoreGraphTests {
    @Test
    void productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs() {
        SupplyGraph graph = SupplyGraph.loadDefault();

        assertTrue(
                graph.items().containsKey("store:minimum"),
                "Фрагмент склада объявляет узел минимума Иначе тест productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs перестанет ловить эту поломку. При смене productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs проверь это утверждение.");
        assertTrue(
                graph.items().containsKey("store:transfer"),
                "Фрагмент склада объявляет узел переноса Иначе тест productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs перестанет ловить эту поломку. При смене productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs проверь это утверждение.");
        assertTrue(
                graph.items().containsKey("store:smelt"),
                "Фрагмент склада объявляет узел печи Иначе тест productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs перестанет ловить эту поломку. При смене productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs проверь это утверждение.");
        assertTrue(
                graph.items().containsKey("store:space"),
                "Фрагмент склада объявляет узел места Иначе тест productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs перестанет ловить эту поломку. При смене productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs проверь это утверждение.");
        assertTrue(
                graph.items().containsKey("store:sort"),
                "Фрагмент склада объявляет узел сортировки Иначе тест productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs перестанет ловить эту поломку. При смене productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs проверь это утверждение.");
        assertTrue(
                graph.way("store_carry_deliver").isPresent(),
                "Путь сдать несомое объявлен Иначе тест productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs перестанет ловить эту поломку. При смене productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs проверь это утверждение.");
        assertTrue(
                graph.way("store_carry_transfer").isPresent(),
                "Путь переноса из сундука A в B объявлен Иначе тест productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs перестанет ловить эту поломку. При смене productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs проверь это утверждение.");
        assertTrue(
                graph.way("store_smelt_load").isPresent(),
                "Путь загрузки печи объявлен Иначе тест productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs перестанет ловить эту поломку. При смене productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs проверь это утверждение.");
        assertTrue(
                graph.way("store_smelt_take").isPresent(),
                "Путь выемки из печи объявлен Иначе тест productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs перестанет ловить эту поломку. При смене productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs проверь это утверждение.");
        assertTrue(
                graph.way("store_space").isPresent(),
                "Путь новой пары сундуков объявлен Иначе тест productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs перестанет ловить эту поломку. При смене productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs проверь это утверждение.");
        assertTrue(
                graph.way("store_sort").isPresent(),
                "Путь сортировки сундуков объявлен Иначе тест productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs перестанет ловить эту поломку. При смене productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs проверь это утверждение.");
        assertTrue(
                graph.implemented("store_carry_deliver"),
                "Сдать несомое исполняется действием склада Иначе тест productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs перестанет ловить эту поломку. При смене productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs проверь это утверждение.");
        assertTrue(
                graph.implemented("store_smelt_load"),
                "Загрузка печи исполняется выплавкой угля Иначе тест productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs перестанет ловить эту поломку. При смене productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs проверь это утверждение.");
        assertTrue(
                graph.implemented("store_sort"),
                "Сортировка исполняется действием склада Иначе тест productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs перестанет ловить эту поломку. При смене productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs проверь это утверждение.");
        assertTrue(
                graph.implemented("store_space"),
                "Новая пара сундуков исполняется существующим кейсом STOCK_CHESTS, а не сторожем: "
                        + "у пути store_space больше нет TODO(store:space) Иначе тест productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs перестанет ловить эту поломку. При смене productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs проверь это утверждение.");
    }

    @Test
    void repairEvent_StockChests_UsesRepairPlanTicketId() {
        SupplyGraph graph = SupplyGraph.loadDefault();
        GraphEvent event = graph.events().get("repair_chests");

        assertTrue(
                event != null,
                "Ремонт двора объявлен узлом repair_chests Иначе тест repairEvent_StockChests_UsesRepairPlanTicketId перестанет ловить эту поломку. При смене repairEvent_StockChests_UsesRepairPlanTicketId проверь это утверждение.");
        assertEquals(
                RepairPlan.PREFIX + "stock_chests",
                event.tasks().get(0).id(),
                "Узел ремонта двора называет тот же билет, что StageRepair для STOCK_CHESTS: "
                        + "иначе граф и исполнитель ставят две разные заявки на одни дыры При смене repairEvent_StockChests_UsesRepairPlanTicketId проверь это утверждение.");
    }

    @Test
    void plan_MinimumShortageAboveGraphThreshold_PostsGather() {
        GraphWorld world = GraphWorld.settled().with("cobblestone", 100);
        GraphPlan plan = plan(world, facts(Map.of("cobblestone", 160), Map.of("cobblestone", 100)));

        assertTrue(
                plan.task("mine_cobble").isPresent(),
                "Складу нужно 160 булыжника, в сундуках 100 (выше порога графа) — добыча обязана появиться:  Иначе тест plan_MinimumShortageAboveGraphThreshold_PostsGather перестанет ловить эту поломку. При смене plan_MinimumShortageAboveGraphThreshold_PostsGather проверь это утверждение."
                        + plan.tasks());
    }

    private static GraphPlan plan(GraphWorld world, GraphFacts facts) {
        return GraphPlanner.plan(
                world.graph, world.state().withFacts(facts, world.graph.costs().life()));
    }

    private static GraphFacts facts(Map<String, Integer> minimum, Map<String, Integer> actual) {
        return new Facts()
                .stageReqs(new GraphSiteFacts.StageReqs(minimum, actual))
                .build();
    }

    private static final class Facts {
        private Map<String, GraphFacts.Spirit> spirits = Map.of();
        private List<GraphFacts.Chest> chests = List.of();
        private Map<String, GraphFacts.Station> stations = Map.of();
        private GraphSiteFacts.StageReqs stageReqs = new GraphSiteFacts.StageReqs(Map.of(), Map.of());

        Facts stageReqs(GraphSiteFacts.StageReqs reqs) {
            stageReqs = reqs;
            return this;
        }

        GraphFacts build() {
            return new GraphFacts(
                    spirits,
                    List.of(),
                    chests,
                    List.of(),
                    stations,
                    Map.of(
                            "furnace",
                            new GraphFacts.Building("furnace", 1, 100, 0, Map.of(), Map.of(), 0, null, false, true)),
                    List.of(),
                    GraphSiteFacts.Terrain.empty(),
                    stageReqs,
                    GraphFeedback.none(),
                    List.of(),
                    List.of());
        }
    }
}
