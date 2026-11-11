package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.SettlementFacts;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphPlanningWiringTests {
    @Test
    void tickets_UnimplementedAndInfeasible_NotPosted() {
        GraphWorld world = GraphWorld.settled().with("coal", 0).with("torch", 0);
        GraphPlan plan = world.plan();

        GraphTickets.Result result = GraphTickets.of(GraphWorld.GRAPH, world.state(), plan);

        assertTrue(
                result.post().stream()
                        .allMatch(ticket ->
                                ticket.task().feasible() && ticket.task().implemented()),
                "На доску идут только выполнимые и реализованные задания: " + result.post());
        assertTrue(
                plan.tasks().stream()
                        .filter(task -> task.implemented() && !task.feasible() && task.raised())
                        .filter(task -> !laterStage(world, task))
                        .allMatch(task -> result.live().contains(GraphTickets.id(task))),
                "Невыполнимое задание своего этапа остаётся живой причиной: поставленный раньше тикет не снимается, "
                        + "пока граф его держит: " + result.live());
        assertTrue(
                result.post().stream().allMatch(ticket -> ticket.id().startsWith(GraphTickets.PREFIX)),
                "id заданий графа стабильны и с префиксом graph: — доска дедуплицирует по id: " + result.post());
    }

    @Test
    void tickets_ChopShortage_CountCoversThresholdGap() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 0);
        GraphWorld.GRAPH.buildings().values().stream()
                .filter(building -> !building.optional() && building.stage() <= world.stage)
                .forEach(building -> world.built(building.id(), Built.BUILT));

        GraphTickets.Result result = GraphTickets.of(GraphWorld.GRAPH, world.state(), world.plan());

        int threshold = GraphLack.threshold(GraphWorld.GRAPH.items().get("oak_log"), world.state());
        assertTrue(
                result.post().stream()
                        .anyMatch(ticket -> "graph:chop_oak_log".equals(ticket.id())
                                && ticket.count() == Math.min(GraphTickets.COUNT_MAX, threshold)),
                "Рубка добирает запас до порога Xmin·P·S·K (" + threshold + "), не больше стака: " + result.post());
    }

    @Test
    void tickets_BuildingBillBiggerThanThreshold_CountCoversRemainingBill() {
        GraphWorld world =
                GraphWorld.settled().with("oak_log", 40).with("oak_planks", 60).with("cobblestone", 64);
        world.materials.put("hut", Map.of("oak_log", 12, "oak_planks", 120, "cobblestone", 16));

        GraphTickets.Result result = GraphTickets.of(GraphWorld.GRAPH, world.state(), world.plan());

        assertTrue(
                result.post().stream()
                        .anyMatch(ticket -> "graph:craft_oak_planks".equals(ticket.id()) && ticket.count() >= 60),
                "Заготовка закрывает счёт постройки, а не только порог предмета: хижине нужно 120 досок, "
                        + "на складе 60 — крафт досок обязан идти на остаток 60, иначе стройка ждёт вечно: "
                        + result.post());
    }

    @Test
    void tickets_SpiritWithoutTools_ToolTaskCarriesSpirit() {
        GraphWorld world = GraphWorld.settled().tools("spirit-1", -1);

        GraphTickets.Result result = GraphTickets.of(GraphWorld.GRAPH, world.state(), world.plan());

        assertTrue(
                result.post().stream()
                        .anyMatch(ticket -> ticket.id().matches("graph:craft_(wooden|stone)_pickaxe@spirit-1")
                                && ticket.task()
                                        .spirit()
                                        .filter("spirit-1"::equals)
                                        .isPresent()),
                "Кирка ставится духу без кирки адресно: набор у каждого духа свой (владелец 23.10): " + result.post());
    }

    @Test
    void plan_NoPickaxeAnywhere_ClearingWaitsPickaxeRaised() {
        GraphPlan plan = GraphWorld.empty()
                .built("crafting_table", Built.BUILT)
                .with("oak_planks", 16)
                .with("stick", 8)
                .plan();

        assertFalse(
                plan.task("building:clearing").map(GraphPlan.Task::feasible).orElse(true),
                "Без кирки выравнивание не начинается: срезать камень нечем: " + plan.task("building:clearing"));
        assertTrue(
                plan.task("craft_wooden_pickaxe@spirit-1")
                        .map(GraphPlan.Task::feasible)
                        .orElse(false),
                "Выравнивание ждёт кирку — деревянная кирка выполнима и стоит заданием: " + plan.tasks());
    }

    @Test
    void stageBuilt_FactsOfFreshPlot_OnlyTableBuilt() {
        SettlementFacts facts = new SettlementFacts(
                true,
                false,
                false,
                1,
                false,
                true,
                Set.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Set.of(BoardCase.STOCK_CHESTS),
                Set.of());

        Map<String, Built> built = StageBuilt.of(facts);

        assertEquals(Built.BUILT, built.get("crafting_table"), "Верстак стоит у дома — постройка готова: " + built);
        assertEquals(
                Built.PLANNED,
                built.get("chests"),
                "Сундуков двора меньше двух — двор не готов, даже если шаг в журнале сделан: " + built);
        assertEquals(StageBuilt.ids(), built.keySet(), "Каждая постройка этапа 1 получает состояние для графа");
    }

    @Test
    void stageBuilt_OldChestsNearHome_DamagedYardStillPlanned() {
        SettlementFacts facts = new SettlementFacts(
                true,
                true,
                true,
                4,
                false,
                true,
                Set.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Set.of(BoardCase.STOCK_CHESTS, BoardCase.CRAFT_FURNACE),
                Set.of());

        Map<String, Built> built = StageBuilt.of(facts);

        assertEquals(
                Built.PLANNED,
                built.get("chests"),
                "Сундуки вне схемы двора не делают двор построенным: иначе задание двора не переиздаётся, "
                        + "а `spirit plan` показывает 0% повреждена: " + built);
        assertEquals(
                Built.PLANNED,
                built.get("furnace"),
                "Печь не на месте схемы двора — печь двора не построена, даже если шаг в журнале сделан: " + built);
    }

    @Test
    void tickets_StageOneNoFood_NoForagingOfStageTwoCrops() {
        GraphWorld world = GraphWorld.settled().with("food", 0);

        GraphTickets.Result result = GraphTickets.of(GraphWorld.GRAPH, world.state(), world.plan());

        assertTrue(
                result.post().stream()
                        .noneMatch(ticket -> "carrot".equals(ticket.task().makes())),
                "Морковь — предмет этапа 2: на этапе 1 голод её не собирает, духи не тратят время на поиск грядок: "
                        + result.post());
    }

    @Test
    void events_FenceAndCrater_ProduceNodeTickets() {
        GraphWorld world =
                GraphWorld.settled().with("dirt", 20).event("fence_broken").event("creeper_crater");

        GraphTickets.Result result = GraphTickets.of(GraphWorld.GRAPH, world.state(), world.plan());

        assertTrue(
                result.post().stream()
                        .anyMatch(ticket -> ticket.task().boardCase() == BoardCase.REPAIR_FENCE
                                && "repair_fence".equals(ticket.task().makes())),
                "Сломанный забор — узел event:fence_broken: без его билета починка остаётся сторожем мимо графа: "
                        + result.post());
        assertTrue(
                result.post().stream()
                        .anyMatch(ticket -> ticket.task().boardCase() == BoardCase.FILL_CRATER
                                && "fill_crater".equals(ticket.task().makes())),
                "Воронка крипера — узел event:creeper_crater: граф сам решает засыпку: " + result.post());
    }

    @Test
    void plan_OrchardAndLeftOre_UseTheirOwnNodes() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 0).built("orchard_oak", Built.BUILT);

        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("harvest_orchard_oak")
                        .filter(task -> task.boardCase() == BoardCase.HARVEST_ORCHARD)
                        .filter(GraphPlan.Task::feasible)
                        .isPresent(),
                "Зрелый сад — путь harvest_orchard_*: рубку выбирает нехватка брёвен, а не сторож: " + plan.tasks());
    }

    @Test
    void plan_LeftOreShortage_SelectsMineLeftOreNode() {
        GraphWorld world = GraphWorld.settled().with("lapis_lazuli", 0).built("mine_level_2", Built.BUILT);
        world.stage = 3;

        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("mine_lapis")
                        .filter(task -> task.boardCase() == BoardCase.MINE_LEFT_ORE)
                        .filter(GraphPlan.Task::feasible)
                        .isPresent(),
                "Лазурит из оставленной руды — путь mine_lapis с кейсом MINE_LEFT_ORE: " + plan.tasks());
    }

    private static boolean laterStage(GraphWorld world, GraphPlan.Task task) {
        SupplyGraph.Item item = GraphWorld.GRAPH.items().get(task.makes());
        return !task.id().contains(":")
                && item != null
                && item.stage() > world.state().stage();
    }

    @Test
    void stageBuilt_Ids_AllAreGraphBuildings() {
        assertTrue(
                GraphWorld.GRAPH.buildings().keySet().containsAll(StageBuilt.ids()),
                "Состояние постройки этапа 1 из фактов участка пишется под id графа; id без постройки в графе — "
                        + "постройка навсегда «не построена» (сад теперь orchard_<порода>): " + StageBuilt.ids());
    }
}
