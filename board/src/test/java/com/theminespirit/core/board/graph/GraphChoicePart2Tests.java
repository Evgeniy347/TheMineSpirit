package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.api.JsonResource;
import org.junit.jupiter.api.Test;

class GraphChoicePart2Tests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

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
                costs.life());
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

    private static GraphWorld dusk() {
        GraphWorld world = GraphWorld.settled();
        world.world.add(MoreRules.DUSK_SOON);
        world.distances.put(MoreRules.DUSK_SOON, 40);
        return world;
    }

    private static GraphWorld duskNear() {
        GraphWorld world = GraphWorld.settled();
        world.world.add(MoreRules.DUSK_SOON);
        return world;
    }

    private static GraphWorld secure() {
        GraphWorld world = GraphWorld.settled();
        world.world.add(MoreRules.FENCE_GAP);
        world.world.add(MoreRules.DOOR_OPEN_NIGHT);
        world.world.add(MoreRules.HOPPER_UNCOVERED);
        return world;
    }

    private static int lowestSecure(GraphPlan plan) {
        return Math.min(
                Math.min(plan.priority("more_secure_fence"), plan.priority("more_secure_door")),
                plan.priority("more_secure_hopper"));
    }

    @Test
    void farTrip_BadConditions_SeasonTravel() {
        GraphWorld calm = dusk();
        GraphPlan neutral = GraphPlanner.plan(GRAPH, calm.state());
        SupplyGraph bad = tuned("{\"farTrip\": 5}");
        GraphWorld storm = dusk();
        storm.world.add(MoreRules.BAD_TRIP_CONDITIONS);
        GraphPlan harsh = GraphPlanner.plan(bad, storm.state());
        assertEquals(
                600,
                neutral.task("more_return_before_night").orElseThrow().ticks(),
                "Дорога 40 блоков: 2·40·5 + 200 Иначе тест farTrip_BadConditions_SeasonTravel перестанет ловить эту поломку. При смене farTrip_BadConditions_SeasonTravel проверь это утверждение.");
        assertEquals(
                2200,
                harsh.task("more_return_before_night").orElseThrow().ticks(),
                "Непогода впятеро удлиняет дорогу Иначе тест farTrip_BadConditions_SeasonTravel перестанет ловить эту поломку. При смене farTrip_BadConditions_SeasonTravel проверь это утверждение.");
        GraphPlan far = GraphPlanner.plan(tuned("{\"farTrip\": 10}"), storm.state());
        assertTrue(
                far.priority("more_return_before_night") < neutral.priority("more_return_before_night"),
                "Дальняя вылазка в непогоду ниже по весу: " + far.priority("more_return_before_night") + " против "
                        + neutral.priority(
                                "more_return_before_night Иначе тест farTrip_BadConditions_SeasonTravel перестанет ловить эту поломку. При смене farTrip_BadConditions_SeasonTravel проверь это утверждение."));
    }

    @Test
    void slowWork_MeasuredSlowdown_RaisesCost() {
        GraphWorld world = duskNear();
        world.event(More3Rules.WORK_YIELD_LOW);
        GraphPlan neutral = GraphPlanner.plan(GRAPH, world.state());
        GraphPlan retuned = GraphPlanner.plan(tuned("{\"slowWork\": 2}"), world.state());
        assertEquals(
                200,
                neutral.task("more_return_before_night").orElseThrow().ticks(),
                "Срок пути из costs Иначе тест slowWork_MeasuredSlowdown_RaisesCost перестанет ловить эту поломку. При смене slowWork_MeasuredSlowdown_RaisesCost проверь это утверждение.");
        assertEquals(
                400,
                retuned.task("more_return_before_night").orElseThrow().ticks(),
                "Замеренная медленная работа удваивает срок, а не получает отдельный путь Иначе тест slowWork_MeasuredSlowdown_RaisesCost перестанет ловить эту поломку. При смене slowWork_MeasuredSlowdown_RaisesCost проверь это утверждение.");
    }

    @Test
    void duplicate_SameWork_TakesPenalty() {
        GraphWorld world = secure();
        int neutral = lowestSecure(GraphPlanner.plan(GRAPH, world.state()));
        world.world.add(MoreRules.WORK_DUPLICATE);
        int penalized = lowestSecure(GraphPlanner.plan(tuned("{\"duplicate\": 0.5}"), world.state()));
        assertTrue(
                penalized < neutral,
                "Вторая работа на тот же узел дешевле: " + penalized
                        + " против  Иначе тест duplicate_SameWork_TakesPenalty перестанет ловить эту поломку. При смене duplicate_SameWork_TakesPenalty проверь это утверждение."
                        + neutral);
    }
}
