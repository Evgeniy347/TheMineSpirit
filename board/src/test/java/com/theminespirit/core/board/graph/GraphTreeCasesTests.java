package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.GraphCaseFixtures.calls;
import static com.theminespirit.core.board.graph.GraphCaseFixtures.facts;
import static com.theminespirit.core.board.graph.GraphCaseFixtures.ids;
import static com.theminespirit.core.board.graph.GraphCaseFixtures.materialPlan;
import static com.theminespirit.core.board.graph.GraphCaseFixtures.tickets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
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
        assertEquals(BoardCase.GATHER_MATERIALS, chop.boardCase(), "Рубка идёт общим сбором материалов");
        assertTrue(chop.feasible(), "Дуб в округе и топор у духа — рубка выполнима: " + chop);
        assertTrue(chop.consumes().isEmpty(), "Рубка ничего не расходует: дерево берут из мира, а не из сундука");

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
                                .toList());
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
                        + "верхушка останется висеть. Параметры: " + chop.params());
        assertEquals(
                true,
                chop.params().get("replant"),
                "Лес восстанавливают посадкой после рубки: иначе вырубка не заканчивается. Параметры: "
                        + chop.params());
        assertTrue(
                calls.stream().anyMatch(call -> "acquire".equals(call.script())),
                "Если рубить нечего, план обязан добрать брёвна сбором, а не падать: " + calls);
    }

    @Test
    void plan_OrchardBuilt_OwnHarvestWayCutsGardenTrees() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 0).built(ORCHARD, Built.BUILT);
        GraphPlan plan = world.plan();

        GraphPlan.Task harvest = plan.task("harvest_orchard_oak")
                .orElseThrow(() -> new AssertionError(
                        "Сад построен, брёвен нет — урожай сада обязан быть на доске: " + ids(plan)));
        assertEquals(
                BoardCase.HARVEST_ORCHARD,
                harvest.boardCase(),
                "Деревья сада снимаются заданием сада (с посадкой на место), а не общим сбором материалов");
        assertTrue(harvest.feasible(), "Сад созрел — сбор урожая выполним: " + harvest);
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
                        + "Параметры: " + chop.params());
        assertTrue(
                calls.stream().anyMatch(call -> "gather".equals(call.script())),
                "После рубки саженцы собирают с листвы отдельным шагом: " + calls);
    }

    @Test
    void plan_OrchardEmptyWithSaplings_ReplantsInsteadOfFelling() {
        GraphWorld world = GraphWorld.settled()
                .with("oak_log", 64)
                .with("oak_sapling", 4)
                .built(ORCHARD, Built.BUILT)
                .event("orchard_empty");
        GraphPlan plan = world.plan();

        GraphPlan.Task replant = plan.task("event:orchard_empty:replant_orchard")
                .orElseThrow(() ->
                        new AssertionError("Сад пуст, саженцы есть — обязана стоять посадка в сад: " + ids(plan)));
        assertEquals(BoardCase.BUILD_ORCHARD, replant.boardCase(), "Посадка в сад идёт заданием сада");
        assertTrue(replant.feasible(), "Саженцы в запасе — посадка выполнима: " + replant);
        assertTrue(
                !plan.raised("gather_oak_sapling"),
                "Саженцев хватает — рубка леса ради них не ставится: " + plan.task("gather_oak_sapling"));
        assertTrue(
                !plan.raised("chop_oak_log"),
                "Брёвен достаточно — фоновая рубка не мешает посадке сада: " + plan.task("chop_oak_log"));
    }

    @Test
    void plan_OrchardComplete_NoReplantWork() {
        GraphWorld world =
                GraphWorld.settled().with("oak_log", 64).with("oak_sapling", 4).built(ORCHARD, Built.BUILT);
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("event:orchard_empty:replant_orchard").isEmpty(),
                "Сад полон — посадки нет: повторная посадка на занятые клетки тратит саженцы впустую. Задания: "
                        + ids(plan));
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
                "Восстановление леса идёт через сад плана, а не новой рубкой дикого леса");
        assertTrue(replant.feasible(), "Саженцы в запасе — посадка выполнима: " + replant);
    }

    @Test
    void plan_NoOakInWorld_NoChopTask() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 0).noWorld(OAK);
        GraphPlan plan = world.plan();

        assertTrue(
                !plan.raised("chop_oak_log"),
                "Дубов в округе нет — рубку не ставят: дух не ищет дерево, которого нет. Задания: " + ids(plan));
    }

    @Test
    void terrain_HangingAfterChop_ClearFloatingPosted() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 64);
        GraphState state = world.state()
                .withFacts(
                        facts(new GraphSiteFacts.Terrain(0, 3, 0, 0, 0)),
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
                "Уборку висящего исполняет расчистка участка, иначе верхушка висит вечно");
    }
}
