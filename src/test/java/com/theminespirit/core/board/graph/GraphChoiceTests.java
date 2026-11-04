package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.JsonResource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphChoiceTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void choice_DefaultGraph_IsNeutral() {
        assertEquals(
                GraphChoice.NEUTRAL,
                GRAPH.costs().choice(),
                "Веса по умолчанию нейтральны: поведение графа не меняется");
    }

    @Test
    void choice_NegativeParameter_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> tuned("{\"farTrip\": -1}"),
                "Отрицательная поправка ломает сравнение весов: её отвергают с объяснением");
    }

    @Test
    void farTrip_BadConditions_SeasonTravel() {
        GraphWorld calm = dusk();
        GraphPlan neutral = GraphPlanner.plan(GRAPH, calm.state());

        SupplyGraph bad = tuned("{\"farTrip\": 3}");
        GraphWorld storm = dusk();
        storm.world.add(MoreRules.BAD_TRIP_CONDITIONS);
        GraphPlan harsh = GraphPlanner.plan(bad, storm.state());

        assertEquals(
                600, neutral.task("more_return_before_night").orElseThrow().ticks(), "Дорога 40 блоков: 2·40·5 + 200");
        assertEquals(1400, harsh.task("more_return_before_night").orElseThrow().ticks(), "Непогода утраивает дорогу");
        assertTrue(
                harsh.priority("more_return_before_night") <= neutral.priority("more_return_before_night"),
                "Дальняя вылазка в непогоду не выше по весу: приоритет целый, после новых весов он может совпасть "
                        + "(усилие видно по срокам 600→1400). Получено "
                        + harsh.priority("more_return_before_night") + " против "
                        + neutral.priority("more_return_before_night"));
    }

    @Test
    void slowWork_MeasuredSlowdown_RaisesCost() {
        GraphWorld world = duskNear();
        world.event(More3Rules.WORK_YIELD_LOW);
        GraphPlan neutral = GraphPlanner.plan(GRAPH, world.state());
        GraphPlan retuned = GraphPlanner.plan(tuned("{\"slowWork\": 2}"), world.state());

        assertEquals(200, neutral.task("more_return_before_night").orElseThrow().ticks(), "Срок пути из costs");
        assertEquals(
                400,
                retuned.task("more_return_before_night").orElseThrow().ticks(),
                "Замеренная медленная работа удваивает срок, а не получает отдельный путь");
    }

    @Test
    void duplicate_SameWork_TakesPenalty() {
        GraphWorld world = secure();
        int neutral = lowestSecure(GraphPlanner.plan(GRAPH, world.state()));
        world.world.add(MoreRules.WORK_DUPLICATE);
        int penalized = lowestSecure(GraphPlanner.plan(tuned("{\"duplicate\": 0.5}"), world.state()));

        assertTrue(penalized < neutral, "Вторая работа на тот же узел дешевле: " + penalized + " против " + neutral);
    }

    @Test
    void conflict_NearWorks_SpreadByWeight() {
        GraphWorld world = secure();
        int neutral = lowestSecure(GraphPlanner.plan(GRAPH, world.state()));
        world.event(More3Rules.WORK_CONFLICT);
        int spread = lowestSecure(GraphPlanner.plan(tuned("{\"conflict\": 0.5}"), world.state()));

        assertTrue(spread < neutral, "Конфликт работ разводит духов весом: " + spread + " против " + neutral);
    }

    @Test
    void pileup_WorkPileup_SpreadsSecondWork() {
        GraphWorld world = secure();
        int neutral = lowestSecure(GraphPlanner.plan(GRAPH, world.state()));
        world.event(More7Rules.WORK_PILEUP);
        int spread = lowestSecure(GraphPlanner.plan(tuned("{\"pileup\": 0.5}"), world.state()));

        assertTrue(spread < neutral, "Скопление работ снижает вес дубля: " + spread + " против " + neutral);
    }

    @Test
    void strategicGoal_OpenGoal_AddsWeight() {
        GraphWorld world = GraphWorld.settled();
        world.world.add(MoreRules.PLAYER_NEAR);
        world.world.add(MoreRules.STRATEGIC_GOAL);
        int neutral = GraphPlanner.plan(GRAPH, world.state()).priority("more_help_player");
        int boosted = GraphPlanner.plan(tuned("{\"strategicBoost\": 10}"), world.state())
                .priority("more_help_player");

        assertTrue(boosted > neutral, "Стратегическая цель поднимает вес работы: " + boosted + " против " + neutral);
    }

    @Test
    void bottleneck_StageSlow_RaisesStageWork() {
        GraphWorld world = GraphWorld.settled();
        world.world.add(MoreRules.PLAYER_NEAR);
        world.event(More3Rules.STAGE_SLOW);
        int neutral = GraphPlanner.plan(GRAPH, world.state()).priority("more_help_player");
        int boosted = GraphPlanner.plan(tuned("{\"bottleneckBoost\": 10}"), world.state())
                .priority("more_help_player");

        assertTrue(boosted > neutral, "Отстающий этап поднимает свою работу: " + boosted + " против " + neutral);
    }

    @Test
    void idleStation_FreeFurnace_RaisesStationWork() {
        GraphWorld world = GraphWorld.settled();
        world.event(More3Rules.STATION_IDLE);
        world.facts(furnaceIdle());
        int neutral = GraphPlanner.plan(GRAPH, world.state()).priority("store_smelt_load");
        int boosted = GraphPlanner.plan(tuned("{\"idleStationBoost\": 10}"), world.state())
                .priority("store_smelt_load");

        assertTrue(boosted > neutral, "Простаивающая станция поднимает свою работу: " + boosted + " против " + neutral);
    }

    @Test
    void nightHome_PlayerSleeps_FarWorkLosesWeight() {
        GraphWorld world = dusk();
        world.event(More3Rules.PLAYER_SLEEP);
        int neutral = GraphPlanner.plan(GRAPH, world.state()).priority("more_return_before_night");
        int home =
                GraphPlanner.plan(tuned("{\"nightHome\": 0.5}"), world.state()).priority("more_return_before_night");

        assertTrue(home < neutral, "Ночью дальняя работа дешевле домашней: " + home + " против " + neutral);
    }

    @Test
    void spaceFound_FreeGround_RaisesBuilding() {
        GraphWorld world = GraphWorld.empty();
        world.event(More4Rules.SPACE_FOUND);
        int neutral = GraphPlanner.plan(GRAPH, world.state()).priority("building:fence");
        int boosted = GraphPlanner.plan(tuned("{\"spaceFoundBoost\": 50}"), world.state())
                .priority("building:fence");

        assertTrue(boosted > neutral, "Свободное место поднимает стройку: " + boosted + " против " + neutral);
    }

    @Test
    void spaceTight_NoGround_LowersBuilding() {
        GraphWorld world = GraphWorld.empty();
        world.event(More4Rules.SPACE_TIGHT);
        int neutral = GraphPlanner.plan(GRAPH, world.state()).priority("building:fence");
        int tight =
                GraphPlanner.plan(tuned("{\"spaceTight\": 0.3}"), world.state()).priority("building:fence");

        assertTrue(tight < neutral, "Тесное место снижает вес стройки: " + tight + " против " + neutral);
    }

    @Test
    void petAlone_TamedPet_LowersGuard() {
        GraphWorld world = guard();
        world.event(More4Rules.TAMED_PET);
        String guard = "event:" + More4Rules.PLAYER_NEAR + ":more4_guard_settlement";
        int neutral = GraphPlanner.plan(GRAPH, world.state()).priority(guard);
        int alone =
                GraphPlanner.plan(tuned("{\"petAlone\": 0.5}"), world.state()).priority(guard);

        assertTrue(alone < neutral, "Приручённого питомца не тревожат лишней охраной: " + alone + " против " + neutral);
    }

    @Test
    void herdFull_FullPen_StopsBreeding() {
        GraphWorld world = herd();
        world.event(More7Rules.PEN_FULL);
        int neutral = GraphPlanner.plan(GRAPH, world.state()).priority("breed_cow");
        int held =
                GraphPlanner.plan(tuned("{\"herdFull\": 0.5}"), world.state()).priority("breed_cow");

        assertTrue(held < neutral, "Полный загон сдерживает разведение: " + held + " против " + neutral);
    }

    @Test
    void fieldFull_FullField_StopsSowing() {
        GraphWorld world = GraphWorld.settled();
        world.event(More2Rules.FOOD_LOW);
        world.event(More7Rules.FIELD_FULL);
        GraphPlan neutral = GraphPlanner.plan(GRAPH, world.state());
        GraphPlan held = GraphPlanner.plan(tuned("{\"fieldFull\": 0.5}"), world.state());

        String sow = "event:" + More2Rules.FOOD_LOW + ":more2_sow_food";
        String breed = "event:" + More2Rules.FOOD_LOW + ":more2_breed_food";
        assertTrue(
                held.priority(sow) < neutral.priority(sow),
                "Полное поле сдерживает посев: " + held.priority(sow) + " против " + neutral.priority(sow));
        assertEquals(neutral.priority(breed), held.priority(breed), "Полное поле не трогает животноводство");
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

    private static GraphWorld guard() {
        GraphWorld world = GraphWorld.settled();
        world.event(More4Rules.PLAYER_NEAR);
        return world;
    }

    private static GraphWorld herd() {
        GraphWorld world = GraphWorld.empty();
        world.stage = 2;
        world.built("pen_cow", GraphState.Built.BUILT);
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

    private static GraphFacts furnaceIdle() {
        GraphFacts.Station furnace = new GraphFacts.Station(
                "furnace", "furnace", new GraphFacts.Position(0, 64, 0), false, "coal", null, null);
        return new GraphFacts(
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of("furnace", furnace),
                Map.of(),
                List.of(),
                GraphFacts.Terrain.empty(),
                new GraphFacts.StageReqs(Map.of(), Map.of()));
    }
}
