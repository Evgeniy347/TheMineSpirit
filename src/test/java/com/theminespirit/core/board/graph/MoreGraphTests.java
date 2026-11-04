package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MoreGraphTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();
    private static final Map<String, String> SIGNALS = signals();

    @Test
    void moreFragment_Nodes_AreDeclaredWithStartingWeights() {
        for (String way : SIGNALS.values()) {
            SupplyGraph.Way path = GRAPH.way(way).orElseThrow();
            assertTrue(
                    GRAPH.items().containsKey(path.makes()),
                    "Путь " + way + " объявляет узел " + path.makes() + " во фрагменте more");
            assertTrue(GRAPH.items().get(path.makes()).base() > 0, "Стартовый вес узла положителен: " + path.makes());
        }
    }

    @Test
    void moreFragment_ImplementedPaths_HaveExecution() {
        for (String way : List.of(
                "more_help_player",
                "more_keep_clear",
                "more_light_dark_spot",
                "more_return_before_night",
                "more_pickup_drops",
                "more_replant",
                "more_sustain_mine",
                "more_secure_fence",
                "more_secure_door",
                "more_secure_hopper",
                "more_share_reserve")) {
            SupplyGraph.Way path = GRAPH.way(way).orElseThrow();
            assertTrue(path.weight() > 0, "Стартовый вес пути положителен: " + way);
            assertTrue(GRAPH.implemented(way), "Действие связано с исполнением: " + way);
        }
    }

    @Test
    void moreFragment_ControlSignals_StayStubs() {
        for (String way : List.of(
                "more_avoid_far_trip",
                "more_failure_backoff",
                "more_station_queue",
                "more_avoid_duplicate",
                "more_strategic_goal")) {
            assertFalse(
                    GRAPH.implemented(way),
                    "Путь " + way + " — сигнал управления (не идти, подождать, не дублировать), "
                            + "а не действие: отдельного исполнения у него нет");
        }
    }

    @Test
    void eachSignal_InputPresent_SelectsItsPath() {
        for (Map.Entry<String, String> signal : SIGNALS.entrySet()) {
            GraphWorld world = GraphWorld.settled();
            world.stage = 2;
            world.world.add(signal.getKey());

            GraphPlan plan = world.plan();

            assertTrue(
                    plan.task(signal.getValue()).isPresent(),
                    "Вход " + signal.getKey() + " обязан выбрать путь " + signal.getValue() + ": " + plan.tasks());
        }
    }

    @Test
    void noMoreSignal_InputAbsent_NoMoreTasks() {
        GraphWorld world = GraphWorld.settled();
        world.stage = 2;

        GraphPlan plan = world.plan();

        for (String way : SIGNALS.values()) {
            assertTrue(plan.task(way).isEmpty(), "Без входа области more задачи нет: " + way);
        }
    }

    @Test
    void securitySignals_EachSignal_FindsOwnRepairPath() {
        for (String signal : List.of(MoreRules.FENCE_GAP, MoreRules.DOOR_OPEN_NIGHT, MoreRules.HOPPER_UNCOVERED)) {
            GraphWorld world = GraphWorld.settled();
            world.world.add(signal);

            GraphPlan plan = world.plan();

            assertTrue(
                    plan.task(SIGNALS.get(signal)).isPresent(),
                    "Сигнал защиты " + signal + " обязан дать свой путь: " + plan.tasks());
        }
    }

    private static Map<String, String> signals() {
        Map<String, String> signals = new LinkedHashMap<>();
        signals.put(MoreRules.PLAYER_NEAR, "more_help_player");
        signals.put(MoreRules.PLAYER_BUILDING, "more_keep_clear");
        signals.put(MoreRules.DARK_SPOTS, "more_light_dark_spot");
        signals.put(MoreRules.DUSK_SOON, "more_return_before_night");
        signals.put(MoreRules.BAD_TRIP_CONDITIONS, "more_avoid_far_trip");
        signals.put(MoreRules.FAILURES_REPEAT, "more_failure_backoff");
        signals.put(MoreRules.STATION_QUEUE, "more_station_queue");
        signals.put(MoreRules.DROPS_EXPIRING, "more_pickup_drops");
        signals.put(MoreRules.FOREST_DEPLETED, "more_replant");
        signals.put(MoreRules.ORE_DEPLETED, "more_sustain_mine");
        signals.put(MoreRules.FENCE_GAP, "more_secure_fence");
        signals.put(MoreRules.DOOR_OPEN_NIGHT, "more_secure_door");
        signals.put(MoreRules.HOPPER_UNCOVERED, "more_secure_hopper");
        signals.put(MoreRules.RESERVE_UNUSED, "more_share_reserve");
        signals.put(MoreRules.WORK_DUPLICATE, "more_avoid_duplicate");
        signals.put(MoreRules.STRATEGIC_GOAL, "more_strategic_goal");
        return Map.copyOf(signals);
    }
}
