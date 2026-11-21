package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.SettlementFacts;
import com.theminespirit.core.board.api.BoardCase;
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
                "На доску идут только выполнимые и реализованные задания:  Иначе тест tickets_UnimplementedAndInfeasible_NotPosted перестанет ловить эту поломку. При смене tickets_UnimplementedAndInfeasible_NotPosted проверь это утверждение."
                        + result.post());
        assertTrue(
                plan.tasks().stream()
                        .filter(task -> task.implemented() && !task.feasible() && task.raised())
                        .filter(task -> !laterStage(world, task))
                        .allMatch(task -> result.live().contains(GraphTickets.id(task))),
                "Невыполнимое задание своего этапа остаётся живой причиной: поставленный раньше тикет не снимается, "
                        + "пока граф его держит:  Иначе тест tickets_UnimplementedAndInfeasible_NotPosted перестанет ловить эту поломку. При смене tickets_UnimplementedAndInfeasible_NotPosted проверь это утверждение."
                        + result.live());
        assertTrue(
                result.post().stream().allMatch(ticket -> ticket.id().startsWith(GraphTickets.PREFIX)),
                "id заданий графа стабильны и с префиксом graph: — доска дедуплицирует по id:  Иначе тест tickets_UnimplementedAndInfeasible_NotPosted перестанет ловить эту поломку. При смене tickets_UnimplementedAndInfeasible_NotPosted проверь это утверждение."
                        + result.post());
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
                "Рубка добирает запас до порога Xmin·P·S·K (" + threshold
                        + "), не больше стака:  Иначе тест tickets_ChopShortage_CountCoversThresholdGap перестанет ловить эту поломку. При смене tickets_ChopShortage_CountCoversThresholdGap проверь это утверждение."
                        + result.post());
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
                        + "на складе 60 — крафт досок обязан идти на остаток 60, иначе стройка ждёт вечно:  При смене tickets_BuildingBillBiggerThanThreshold_CountCoversRemainingBill проверь это утверждение."
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
                "Кирка ставится духу без кирки адресно: набор у каждого духа свой (владелец 23.10):  Иначе тест tickets_SpiritWithoutTools_ToolTaskCarriesSpirit перестанет ловить эту поломку. При смене tickets_SpiritWithoutTools_ToolTaskCarriesSpirit проверь это утверждение."
                        + result.post());
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
                "Без кирки выравнивание не начинается: срезать камень нечем: "
                        + plan.task(
                                "building:clearing Иначе тест plan_NoPickaxeAnywhere_ClearingWaitsPickaxeRaised перестанет ловить эту поломку. При смене plan_NoPickaxeAnywhere_ClearingWaitsPickaxeRaised проверь это утверждение."));
        assertTrue(
                plan.task("craft_wooden_pickaxe@spirit-1")
                        .map(GraphPlan.Task::feasible)
                        .orElse(false),
                "Выравнивание ждёт кирку — деревянная кирка выполнима и стоит заданием:  Иначе тест plan_NoPickaxeAnywhere_ClearingWaitsPickaxeRaised перестанет ловить эту поломку. При смене plan_NoPickaxeAnywhere_ClearingWaitsPickaxeRaised проверь это утверждение."
                        + plan.tasks());
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

        assertEquals(
                Built.BUILT,
                built.get("crafting_table"),
                "Верстак стоит у дома — постройка готова:  Иначе тест stageBuilt_FactsOfFreshPlot_OnlyTableBuilt перестанет ловить эту поломку. При смене stageBuilt_FactsOfFreshPlot_OnlyTableBuilt проверь это утверждение."
                        + built);
        assertEquals(
                Built.PLANNED,
                built.get("chests"),
                "Сундуков двора меньше двух — двор не готов, даже если шаг в журнале сделан:  Иначе тест stageBuilt_FactsOfFreshPlot_OnlyTableBuilt перестанет ловить эту поломку. При смене stageBuilt_FactsOfFreshPlot_OnlyTableBuilt проверь это утверждение."
                        + built);
        assertEquals(
                StageBuilt.ids(),
                built.keySet(),
                "Каждая постройка этапа 1 получает состояние для графа Иначе тест stageBuilt_FactsOfFreshPlot_OnlyTableBuilt перестанет ловить эту поломку. При смене stageBuilt_FactsOfFreshPlot_OnlyTableBuilt проверь это утверждение.");
    }

    @Test
    void stageBuilt_ClearingDoneInJournalButPlotUneven_ClearingPlanned() {
        SettlementFacts facts = new SettlementFacts(
                true,
                true,
                false,
                0,
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
                Set.of(BoardCase.CLEAR_GROUNDS),
                Set.of());

        assertEquals(
                Built.PLANNED,
                StageBuilt.of(facts).get("clearing"),
                "Расчистка в журнале выполнена, а участок не ровный — готовность по аудиту мира, а не по журналу: "
                        + "иначе сверка ставит только ремонт, а выравнивание этапа не переиздаётся При смене stageBuilt_ClearingDoneInJournalButPlotUneven_ClearingPlanned проверь это утверждение.");
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
                        + "а `spirit plan` показывает 0% повреждена:  При смене stageBuilt_OldChestsNearHome_DamagedYardStillPlanned проверь это утверждение."
                        + built);
        assertEquals(
                Built.PLANNED,
                built.get("furnace"),
                "Печь не на месте схемы двора — печь двора не построена, даже если шаг в журнале сделан:  Иначе тест stageBuilt_OldChestsNearHome_DamagedYardStillPlanned перестанет ловить эту поломку. При смене stageBuilt_OldChestsNearHome_DamagedYardStillPlanned проверь это утверждение."
                        + built);
    }

    @Test
    void tickets_StageOneNoFood_NoForagingOfStageTwoCrops() {
        GraphWorld world = GraphWorld.settled().with("food", 0);

        GraphTickets.Result result = GraphTickets.of(GraphWorld.GRAPH, world.state(), world.plan());

        assertTrue(
                result.post().stream()
                        .noneMatch(ticket -> "carrot".equals(ticket.task().makes())),
                "Морковь — предмет этапа 2: на этапе 1 голод её не собирает, духи не тратят время на поиск грядок:  Иначе тест tickets_StageOneNoFood_NoForagingOfStageTwoCrops перестанет ловить эту поломку. При смене tickets_StageOneNoFood_NoForagingOfStageTwoCrops проверь это утверждение."
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
                "Сломанный забор — узел event:fence_broken: без его билета починка остаётся сторожем мимо графа:  Иначе тест events_FenceAndCrater_ProduceNodeTickets перестанет ловить эту поломку. При смене events_FenceAndCrater_ProduceNodeTickets проверь это утверждение."
                        + result.post());
        assertTrue(
                result.post().stream()
                        .anyMatch(ticket -> ticket.task().boardCase() == BoardCase.FILL_CRATER
                                && "fill_crater".equals(ticket.task().makes())),
                "Воронка крипера — узел event:creeper_crater: граф сам решает засыпку:  Иначе тест events_FenceAndCrater_ProduceNodeTickets перестанет ловить эту поломку. При смене events_FenceAndCrater_ProduceNodeTickets проверь это утверждение."
                        + result.post());
    }

    @Test
    void plan_OrchardAndLeftOre_UseTheirOwnNodes() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 0).world("orchard:oak");

        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("harvest_orchard_oak")
                        .filter(task -> task.boardCase() == BoardCase.HARVEST_ORCHARD)
                        .filter(GraphPlan.Task::feasible)
                        .isPresent(),
                "Зрелый сад — путь harvest_orchard_*: рубку выбирает нехватка брёвен, а не сторож:  Иначе тест plan_OrchardAndLeftOre_UseTheirOwnNodes перестанет ловить эту поломку. При смене plan_OrchardAndLeftOre_UseTheirOwnNodes проверь это утверждение."
                        + plan.tasks());
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
                "Лазурит из оставленной руды — путь mine_lapis с кейсом MINE_LEFT_ORE:  Иначе тест plan_LeftOreShortage_SelectsMineLeftOreNode перестанет ловить эту поломку. При смене plan_LeftOreShortage_SelectsMineLeftOreNode проверь это утверждение."
                        + plan.tasks());
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
                        + "постройка навсегда «не построена» (сад теперь orchard_<порода>):  Иначе тест stageBuilt_Ids_AllAreGraphBuildings перестанет ловить эту поломку. При смене stageBuilt_Ids_AllAreGraphBuildings проверь это утверждение."
                        + StageBuilt.ids());
    }
}
