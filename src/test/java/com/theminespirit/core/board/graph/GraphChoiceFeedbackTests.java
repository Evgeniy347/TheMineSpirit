package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.JsonResource;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphChoiceFeedbackTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void failureBackoff_RepeatedFailure_LowersTaskWeight() {
        GraphPlan neutral = GraphPlanner.plan(GRAPH, dusk().state());
        GraphPlan penalized = GraphPlanner.plan(GRAPH, failed(dusk(), 1).state());

        assertTrue(
                penalized.priority("more_return_before_night") < neutral.priority("more_return_before_night"),
                "Провал узла снижает его вес на время окна, а не выбирает ту же работу снова: "
                        + penalized.priority("more_return_before_night") + " против "
                        + neutral.priority("more_return_before_night"));
    }

    @Test
    void failureBackoff_ManyFailures_CapThePenalty() {
        GraphPlan one = GraphPlanner.plan(GRAPH, failed(dusk(), 1).state());
        GraphPlan capped = GraphPlanner.plan(GRAPH, failed(dusk(), 9).state());

        assertTrue(
                capped.priority("more_return_before_night") < one.priority("more_return_before_night"),
                "Девять провалов весят меньше одного: " + capped.priority("more_return_before_night") + " против "
                        + one.priority("more_return_before_night"));
    }

    @Test
    void avoidFarTrip_RecentFailure_SeasonsTravelLikeBadWeather() {
        GraphPlan plan =
                GraphPlanner.plan(tuned("{\"farTrip\": 5}"), failed(dusk(), 1).state());

        assertEquals(
                2200,
                plan.task("more_return_before_night").orElseThrow().ticks(),
                "Провал дальней дороги удлиняет её по правилу avoid_far_trip: та же цель не выбирается снова");
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

    private static GraphWorld failed(GraphWorld world, int count) {
        GraphFeedback.Failure failure = new GraphFeedback.Failure(
                "more_return_before_night", "0,64,0", count, GraphFeedback.Cause.NO_PATH, 100);
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
                choice);
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
