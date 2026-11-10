package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.GraphBlock;
import com.theminespirit.core.board.GraphBlock.Need;
import com.theminespirit.core.inventory.JsonResource;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class GraphChoiceFeedbackTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void failureBackoff_ManyDistinctTargets_LowerTaskWeight() {
        GraphPlan neutral = GraphPlanner.plan(GRAPH, dusk().state());
        GraphPlan penalized = GraphPlanner.plan(GRAPH, failedMany(dusk(), 1, 2).state());

        assertTrue(
                penalized.priority("more_return_before_night") < neutral.priority("more_return_before_night"),
                "Провал разных целей снижает вес узла, а не выбирает ту же работу снова: "
                        + penalized.priority("more_return_before_night") + " против "
                        + neutral.priority("more_return_before_night"));
    }

    @Test
    void failureBackoff_ManyFailures_CapThePenalty() {
        GraphPlan two = GraphPlanner.plan(GRAPH, failedMany(dusk(), 1, 2).state());
        GraphPlan capped = GraphPlanner.plan(GRAPH, failedMany(dusk(), 9, 4).state());

        assertTrue(
                capped.priority("more_return_before_night") < two.priority("more_return_before_night"),
                "Много провалов разных целей весит меньше двух: " + capped.priority("more_return_before_night")
                        + " против " + two.priority("more_return_before_night"));
    }

    @Test
    void avoidFarTrip_OneUnreachableTarget_KeepsTravel() {
        SupplyGraph tuned = tuned("{\"farTrip\": 5}");
        GraphWorld calmWorld = dusk();
        int calm = new GraphChoiceRules(tuned, calmWorld.state()).travelTicks("more_return_before_night");
        int one = new GraphChoiceRules(tuned, failed(dusk(), 1).state()).travelTicks("more_return_before_night");
        int many =
                new GraphChoiceRules(tuned, failedMany(dusk(), 1, 2).state()).travelTicks("more_return_before_night");

        assertEquals(calm, one, "Одна недоступная цель удлиняет только свой поход, а не дорогу всему узлу");
        assertTrue(
                many > calm,
                "Провал разных целей — узел плох: дорога удлиняется по avoid_far_trip: " + many + " против " + calm);
    }

    @Test
    void failureBackoff_UnknownNeed_NoPenalty() {
        GraphWorld world = GraphWorld.settled();
        double calm = new GraphChoiceRules(GRAPH, world.state()).value("more_return_before_night", 1.0, false);
        double other = new GraphChoiceRules(
                        GRAPH,
                        failedCause(world, "more_return_before_night", "0,64,0", 2, null)
                                .state())
                .value("more_return_before_night", 1.0, false);
        double unreachable = new GraphChoiceRules(
                        GRAPH, failedMany(dusk(), 1, 2).state())
                .value("more_return_before_night", 1.0, false);

        assertEquals(
                calm,
                other,
                1e-9,
                "Провал без типизированной нужды не штрафует узел: причина едет типом, а не догадкой");
        assertTrue(
                other > unreachable,
                "Провал без нужды мягче доказанной недостижимости двух целей: " + other + " против " + unreachable);
    }

    @Test
    void failureBackoff_OneUnreachableTarget_DoesNotPenalizeWholeNode() {
        GraphWorld world = GraphWorld.settled();
        double calm = new GraphChoiceRules(GRAPH, world.state()).value("more_return_before_night", 1.0, false);
        double penalized = new GraphChoiceRules(
                        GRAPH,
                        failedCause(world, "more_return_before_night", "0,64,0", 5, new GraphBlock.Path("0,64,0"))
                                .state())
                .value("more_return_before_night", 1.0, false);

        assertEquals(
                calm,
                penalized,
                1e-9,
                "Один недоступный ствол не штрафует рубку везде: пока плоха одна цель, узел сохраняет вес и "
                        + "переходит к другой цели: " + penalized + " против " + calm);
    }

    @Test
    void failureBackoff_ChunkFailure_NoPenalty() {
        GraphWorld world = GraphWorld.settled();
        double calm = new GraphChoiceRules(GRAPH, world.state()).value("more_return_before_night", 1.0, false);
        double penalized = new GraphChoiceRules(
                        GRAPH,
                        failedCause(world, "more_return_before_night", "0,64,0", 5, null)
                                .state())
                .value("more_return_before_night", 1.0, false);

        assertEquals(
                calm,
                penalized,
                1e-9,
                "Незагруженный чанк — не провал узла: чанк подгружается, и задание повторяется: " + penalized
                        + " против " + calm);
    }

    @Test
    void failureBackoff_NoMaterial_NoNodePenalty() {
        GraphWorld world = GraphWorld.settled();
        double calm = new GraphChoiceRules(GRAPH, world.state()).value("more_return_before_night", 1.0, false);
        double penalized = new GraphChoiceRules(
                        GRAPH,
                        failedCause(world, "more_return_before_night", "0,64,0", 5, new GraphBlock.Item("dirt", 1))
                                .state())
                .value("more_return_before_night", 1.0, false);

        assertEquals(
                calm,
                penalized,
                1e-9,
                "Нет материала — вопрос снабжения, а не узла: штраф узла гнал бы работу прочь вместо добычи "
                        + "входа: " + penalized + " против " + calm);
    }

    @Test
    void failureBackoff_NoMaterial_KeepsSupplyInputWork() {
        GraphWorld world = GraphWorld.settled();
        world.with("jungle_log", 0);
        double calm = new GraphChoiceRules(GRAPH, world.state()).value("craft_jungle_planks", 1.0, false);
        double failed = new GraphChoiceRules(
                        GRAPH,
                        failedCause(world, "craft_jungle_planks", "0,64,0", 5, new GraphBlock.Item("dirt", 1))
                                .state())
                .value("craft_jungle_planks", 1.0, false);

        assertEquals(
                calm,
                failed,
                1e-9,
                "Нет материала — нехватка на входе снабжения, а не провал узла: " + failed + " против " + calm);
        assertTrue(
                AllocationCases.tickets(world).post().stream()
                        .anyMatch(ticket -> "jungle_log".equals(ticket.task().makes())),
                "Нехватка входа остаётся работой снабжения: граф добывает вход, а не прячет узел");
    }

    @Test
    void conditionTag_NotResource_NoExploreRequest() {
        GraphWorld world = GraphWorld.settled();
        world.world.remove(MoreRules.DUSK_SOON);

        GraphPlan plan = GraphPlanner.plan(GRAPH, world.state());

        assertTrue(
                plan.notices().stream().noneMatch(notice -> notice.reason().contains(MoreRules.DUSK_SOON)),
                "Условие времени суток не «нет источника»: граф не просит разведать закат: " + plan.notices());
        assertTrue(
                plan.task("explore:" + MoreRules.DUSK_SOON).isEmpty(),
                "Незаданное условие не превращается в задание разведки: " + plan.tasks());
    }

    private static GraphWorld dusk() {
        GraphWorld world = GraphWorld.settled();
        world.world.add(MoreRules.DUSK_SOON);
        world.distances.put(MoreRules.DUSK_SOON, 40);
        return world;
    }

    @Test
    void failureBackoff_LifeEvent_NotPenalized() {
        GraphWorld world = GraphWorld.settled().event("life_threat");
        GraphPlan calm = GraphPlanner.plan(GRAPH, world.state());
        GraphPlan penalized = GraphPlanner.plan(
                GRAPH, failedNode(world, "event:life_threat:defend", 3).state());

        assertEquals(
                calm.priority("event:life_threat:defend"),
                penalized.priority("event:life_threat:defend"),
                "Защита жизни не штрафуется памятью неудач: она меняет способ, а не отступает с максимального веса");
    }

    @Test
    void failureBackoff_RescueSpirit_NotPenalized() {
        GraphWorld world = GraphWorld.settled();
        double calm = new GraphChoiceRules(GRAPH, world.state()).value("rescue_spirit", 1.0, false);
        double penalized = new GraphChoiceRules(
                        GRAPH, failedNode(world, "rescue_spirit", 3).state())
                .value("rescue_spirit", 1.0, false);

        assertEquals(
                calm,
                penalized,
                1e-9,
                "Спасение духа не штрафуется памятью неудач: выход меняется (помощник/выход), вес остаётся максимальным");
    }

    @Test
    void failureBackoff_OrdinaryNode_StillPenalized() {
        GraphWorld world = GraphWorld.settled();
        double calm = new GraphChoiceRules(GRAPH, world.state()).value("more_return_before_night", 1.0, false);
        double penalized = new GraphChoiceRules(GRAPH, failedMany(world, 3, 2).state())
                .value("more_return_before_night", 1.0, false);

        assertTrue(
                penalized < calm,
                "Обычный узел по-прежнему штрафуется: послабление только для жизни: " + penalized + " против " + calm);
    }

    private static GraphWorld failed(GraphWorld world, int count) {
        return failedMany(world, count, 1);
    }

    private static GraphWorld failedMany(GraphWorld world, int count, int targets) {
        List<GraphFeedback.Failure> failures = new ArrayList<>();
        for (int index = 0; index < targets; index++) {
            failures.add(new GraphFeedback.Failure(
                    "more_return_before_night", index + ",64,0", count, new GraphBlock.Path(index + ",64,0"), 100));
        }
        return world.facts(world.facts.withFeedback(
                new GraphFeedback(GraphFeedback.Clock.unknown(), GraphFeedback.Player.none(), failures)));
    }

    private static GraphWorld failedCause(
            GraphWorld world, String node, String target, int count, @Nullable Need cause) {
        GraphFeedback.Failure failure = new GraphFeedback.Failure(node, target, count, cause, 100);
        return world.facts(world.facts.withFeedback(
                new GraphFeedback(GraphFeedback.Clock.unknown(), GraphFeedback.Player.none(), List.of(failure))));
    }

    private static GraphWorld failedNode(GraphWorld world, String node, int count) {
        GraphFeedback.Failure failure =
                new GraphFeedback.Failure(node, "0,64,0", count, new GraphBlock.Path("0,64,0"), 100);
        return world.facts(world.facts.withFeedback(
                new GraphFeedback(GraphFeedback.Clock.unknown(), GraphFeedback.Player.none(), List.of(failure))));
    }

    private static SupplyGraph tuned(String json) {
        GraphChoice choice = GraphChoice.parse(JsonResource.parse(json, "test:choice"));
        GraphCosts costs = GRAPH.costs();
        GraphCosts tuned = new GraphCosts(
                costs.defaultTicks(),
                costs.caseTicks(),
                costs.wayTicks(),
                costs.wayUses(),
                costs.ticksPerBlock(),
                costs.travelScaleTicks(),
                costs.toolDurability(),
                costs.stationCapacity(),
                choice,
                costs.life(),
                costs.undo());
        return new SupplyGraph(
                GRAPH.decay(),
                GRAPH.urgentBoost(),
                GRAPH.buildReadyBoost(),
                GRAPH.maxPriority(),
                GRAPH.tiers(),
                GRAPH.kit(),
                GRAPH.storage(),
                GRAPH.items(),
                GRAPH.buildings(),
                GRAPH.ways(),
                GRAPH.edges(),
                GRAPH.unimplemented(),
                GRAPH.events(),
                GRAPH.groups(),
                GRAPH.triggers(),
                GRAPH.herdCaps(),
                GRAPH.buildStartShare(),
                tuned);
    }
}
