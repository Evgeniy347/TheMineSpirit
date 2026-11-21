package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.GraphCaseFixtures.calls;
import static com.theminespirit.core.board.graph.GraphCaseFixtures.facts;
import static com.theminespirit.core.board.graph.GraphCaseFixtures.ids;
import static com.theminespirit.core.board.graph.GraphCaseFixtures.materialPlan;
import static com.theminespirit.core.board.graph.GraphCaseFixtures.tickets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.plan.ScriptCall;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphTreeCasesTests {
    private static final String OAK = "tree:oak";
    private static final String ORCHARD = "orchard_oak";

    @Test
    void plan_WholeTreeChop_IsSingleTreeJobWithReplantAndCollect() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 0).with("oak_planks", 0);
        GraphPlan plan = world.plan();

        GraphPlan.Task chop = plan.task("chop_oak_log")
                .orElseThrow(() -> new AssertionError("Без брёвен граф обязан поставить рубку: " + ids(plan)));
        assertEquals(
                BoardCase.GATHER_MATERIALS,
                chop.boardCase(),
                "Рубка идёт общим сбором материалов"
                        + " Иначе тест plan_WholeTreeChop_IsSingleTreeJobWithReplantAndCollect перестанет ловить эту поломку. При смене plan_WholeTreeChop_IsSingleTreeJobWithReplantAndCollect проверь это утверждение.");
        assertTrue(
                chop.feasible(),
                "Дуб в округе и топор у духа — рубка выполнима: " + chop
                        + " Иначе тест plan_WholeTreeChop_IsSingleTreeJobWithReplantAndCollect перестанет ловить эту поломку. При смене plan_WholeTreeChop_IsSingleTreeJobWithReplantAndCollect проверь это утверждение.");
        assertTrue(
                chop.consumes().isEmpty(),
                "Рубка ничего не расходует: дерево берут из мира, а не из сундука"
                        + " Иначе тест plan_WholeTreeChop_IsSingleTreeJobWithReplantAndCollect перестанет ловить эту поломку. При смене plan_WholeTreeChop_IsSingleTreeJobWithReplantAndCollect проверь это утверждение.");

        long jobs = tickets(world.state()).post().stream()
                .filter(ticket -> "chop_oak_log".equals(ticket.node()))
                .count();
        assertEquals(
                1,
                jobs,
                "Одно дерево — одно задание рубки: иначе несколько духов рубят один ствол по бревну и "
                        + "верхушка остаётся висеть. Задания: "
                        + tickets(world.state()).post().stream()
                                .map(GraphTickets.Ticket::id)
                                .toList()
                        + " При смене plan_WholeTreeChop_IsSingleTreeJobWithReplantAndCollect проверь это утверждение.");
    }

    @Test
    void plan_OakLogBatch_ChopTreeAsksWholeTreesAndReplants() {
        List<ScriptCall> calls = calls(materialPlan("oak_log", 8));

        ScriptCall chop = calls.stream()
                .filter(call -> "chop_tree".equals(call.script()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Заготовка брёвен рубит дерево действием chop_tree: " + calls));
        assertEquals(
                2,
                chop.params().get("count"),
                "8 брёвен — это 2 целых дерева по 4 бревна, а не 8 отдельных блоков: рубить по бревну нельзя, "
                        + "верхушка останется висеть. Параметры: " + chop.params()
                        + " Иначе тест plan_OakLogBatch_ChopTreeAsksWholeTreesAndReplants перестанет ловить эту поломку. При смене plan_OakLogBatch_ChopTreeAsksWholeTreesAndReplants проверь это утверждение.");
        assertEquals(
                true,
                chop.params().get("replant"),
                "Лес восстанавливают посадкой после рубки: иначе вырубка не заканчивается. Параметры: " + chop.params()
                        + " При смене plan_OakLogBatch_ChopTreeAsksWholeTreesAndReplants проверь это утверждение.");
        assertTrue(
                calls.stream().anyMatch(call -> "acquire".equals(call.script())),
                "Если рубить нечего, план обязан добрать брёвна сбором, а не падать: " + calls
                        + " Иначе тест plan_OakLogBatch_ChopTreeAsksWholeTreesAndReplants перестанет ловить эту поломку. При смене plan_OakLogBatch_ChopTreeAsksWholeTreesAndReplants проверь это утверждение.");
    }

    @Test
    void plan_OrchardTreeGrown_OwnHarvestWayCutsGardenTree() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 0).world("orchard:oak");
        GraphPlan plan = world.plan();

        GraphPlan.Task harvest = plan.task("harvest_orchard_oak")
                .orElseThrow(() -> new AssertionError(
                        "В саду выросло дерево, брёвен нет — урожай сада обязан быть на доске: " + ids(plan)));
        assertEquals(
                BoardCase.HARVEST_ORCHARD,
                harvest.boardCase(),
                "Деревья сада снимаются заданием сада (с посадкой на место), а не общим сбором материалов"
                        + " Иначе тест plan_OrchardTreeGrown_OwnHarvestWayCutsGardenTree перестанет ловить эту поломку. При смене plan_OrchardTreeGrown_OwnHarvestWayCutsGardenTree проверь это утверждение.");
        assertTrue(
                harvest.feasible(),
                "Сад созрел — сбор урожая выполним: " + harvest
                        + " Иначе тест plan_OrchardTreeGrown_OwnHarvestWayCutsGardenTree перестанет ловить эту поломку. При смене plan_OrchardTreeGrown_OwnHarvestWayCutsGardenTree проверь это утверждение.");
    }

    @Test
    void plan_OrchardBuiltWithoutGrownTree_NoHarvestWay() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 0).built(ORCHARD, Built.BUILT);
        GraphPlan plan = world.plan();

        assertTrue(
                !plan.raised("harvest_orchard_oak"),
                "Построенный сад без выросшего дерева не рубится: метку мира orchard:oak даёт только ствол "
                        + "на месте саженца. Задания: " + ids(plan)
                        + " Иначе тест plan_OrchardBuiltWithoutGrownTree_NoHarvestWay перестанет ловить эту поломку. При смене plan_OrchardBuiltWithoutGrownTree_NoHarvestWay проверь это утверждение.");
    }

    @Test
    void plan_SaplingGather_DoesNotReplantFelledSpot() {
        List<ScriptCall> calls = calls(materialPlan("oak_sapling", 4));

        ScriptCall chop = calls.stream()
                .filter(call -> "chop_tree".equals(call.script()))
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("Саженцы добывают с листвы, но дерево приходится рубить: " + calls));
        assertEquals(
                false,
                chop.params().get("replant"),
                "Сбор саженцев не сажает их на месте срубленного дерева вне сада: посадка — только в сад плана. "
                        + "Параметры: " + chop.params()
                        + " Иначе тест plan_SaplingGather_DoesNotReplantFelledSpot перестанет ловить эту поломку. При смене plan_SaplingGather_DoesNotReplantFelledSpot проверь это утверждение.");
        assertTrue(
                calls.stream().anyMatch(call -> "gather".equals(call.script())),
                "После рубки саженцы собирают с листвы отдельным шагом: " + calls
                        + " Иначе тест plan_SaplingGather_DoesNotReplantFelledSpot перестанет ловить эту поломку. При смене plan_SaplingGather_DoesNotReplantFelledSpot проверь это утверждение.");
    }

    @Test
    void plan_OrchardEmptyWithSaplings_ReplantsWithoutWaitingBuild() {
        GraphWorld world =
                GraphWorld.settled().with("oak_log", 64).with("oak_sapling", 4).event("orchard_empty");
        GraphPlan plan = world.plan();

        GraphPlan.Task replant = plan.task("event:orchard_empty:replant_orchard")
                .orElseThrow(() ->
                        new AssertionError("Сад пуст, саженцы есть — обязана стоять посадка в сад: " + ids(plan)));
        assertEquals(
                BoardCase.BUILD_ORCHARD,
                replant.boardCase(),
                "Посадка в сад идёт заданием сада"
                        + " Иначе тест plan_OrchardEmptyWithSaplings_ReplantsWithoutWaitingBuild перестанет ловить эту поломку. При смене plan_OrchardEmptyWithSaplings_ReplantsWithoutWaitingBuild проверь это утверждение.");
        assertTrue(
                replant.feasible(),
                "Саженцы в запасе — посадка выполнима: " + replant
                        + " Иначе тест plan_OrchardEmptyWithSaplings_ReplantsWithoutWaitingBuild перестанет ловить эту поломку. При смене plan_OrchardEmptyWithSaplings_ReplantsWithoutWaitingBuild проверь это утверждение.");
        assertTrue(
                !plan.raised("gather_oak_sapling"),
                "Саженцев хватает — рубка леса ради них не ставится: " + plan.task("gather_oak_sapling")
                        + " Иначе тест plan_OrchardEmptyWithSaplings_ReplantsWithoutWaitingBuild перестанет ловить эту поломку. При смене plan_OrchardEmptyWithSaplings_ReplantsWithoutWaitingBuild проверь это утверждение.");
        assertTrue(
                !plan.raised("chop_oak_log"),
                "Брёвен достаточно — фоновая рубка не мешает посадке сада: " + plan.task("chop_oak_log")
                        + " Иначе тест plan_OrchardEmptyWithSaplings_ReplantsWithoutWaitingBuild перестанет ловить эту поломку. При смене plan_OrchardEmptyWithSaplings_ReplantsWithoutWaitingBuild проверь это утверждение.");
    }

    @Test
    void plan_OrchardComplete_NoReplantWork() {
        GraphWorld world =
                GraphWorld.settled().with("oak_log", 64).with("oak_sapling", 4).built(ORCHARD, Built.BUILT);
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("event:orchard_empty:replant_orchard").isEmpty(),
                "Сад полон — посадки нет: повторная посадка на занятые клетки тратит саженцы впустую. Задания: "
                        + ids(plan)
                        + " Иначе тест plan_OrchardComplete_NoReplantWork перестанет ловить эту поломку. При смене plan_OrchardComplete_NoReplantWork проверь это утверждение.");
    }

    @Test
    void plan_ForestDepleted_ReplantsOnlyInOrchard() {
        GraphWorld world = GraphWorld.settled()
                .with("oak_sapling", 8)
                .built(ORCHARD, Built.BUILT)
                .world(MoreRules.FOREST_DEPLETED);
        GraphPlan plan = world.plan();

        GraphPlan.Task replant = plan.task("more_replant")
                .orElseThrow(() ->
                        new AssertionError("Лес вырублен, саженцы есть — посадка обязана быть на доске: " + ids(plan)));
        assertEquals(
                BoardCase.HARVEST_ORCHARD,
                replant.boardCase(),
                "Восстановление леса идёт через сад плана, а не новой рубкой дикого леса"
                        + " Иначе тест plan_ForestDepleted_ReplantsOnlyInOrchard перестанет ловить эту поломку. При смене plan_ForestDepleted_ReplantsOnlyInOrchard проверь это утверждение.");
        assertTrue(
                replant.feasible(),
                "Саженцы в запасе — посадка выполнима: " + replant
                        + " Иначе тест plan_ForestDepleted_ReplantsOnlyInOrchard перестанет ловить эту поломку. При смене plan_ForestDepleted_ReplantsOnlyInOrchard проверь это утверждение.");
    }

    @Test
    void plan_NoOakInWorld_NoChopTask() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 0).noWorld(OAK);
        GraphPlan plan = world.plan();

        assertTrue(
                !plan.raised("chop_oak_log"),
                "Дубов в округе нет — рубку не ставят: дух не ищет дерево, которого нет. Задания: " + ids(plan)
                        + " Иначе тест plan_NoOakInWorld_NoChopTask перестанет ловить эту поломку. При смене plan_NoOakInWorld_NoChopTask проверь это утверждение.");
    }

    @Test
    void terrain_HangingAfterChop_ClearFloatingPosted() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 64);
        GraphState state = world.state()
                .withFacts(
                        facts(new GraphSiteFacts.Terrain(0, 3, 0, 0)),
                        world.graph.costs().life());

        GraphTickets.Ticket cleanup = tickets(state).post().stream()
                .filter(ticket -> GraphWorldEvents.CLEAR_FLOATING.equals(ticket.node()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Висящая верхушка после рубки обязана стать заданием уборки: "
                        + tickets(state).post().stream()
                                .map(GraphTickets.Ticket::id)
                                .toList()));
        assertEquals(
                BoardCase.CLEAR_GROUNDS,
                cleanup.task().boardCase(),
                "Уборку висящего исполняет расчистка участка, иначе верхушка висит вечно"
                        + " При смене terrain_HangingAfterChop_ClearFloatingPosted проверь это утверждение.");
    }
}
