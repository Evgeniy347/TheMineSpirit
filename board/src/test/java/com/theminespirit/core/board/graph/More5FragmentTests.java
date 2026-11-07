package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class More5FragmentTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();
    private static final List<String> EVENTS = List.of(
            "more5_player_weak",
            "more5_crops_dark",
            "more5_saplings_dark",
            "more5_crops_dry",
            "more5_growth_unfed",
            "more5_fuel_low",
            "more5_build_shortage",
            "more5_mob_broken",
            "more5_route_blocked",
            "more5_chest_far",
            "more5_mine_dark",
            "more5_wood_drops");
    private static final List<String> WAYS = List.of(
            "more5_defend_player",
            "more5_feed_player",
            "more5_light_crops",
            "more5_light_saplings",
            "more5_water_crops",
            "more5_fertilize_growth",
            "more5_gather_fuel",
            "more5_make_charcoal",
            "more5_gather_build_material",
            "more5_craft_build_material",
            "more5_restore_block",
            "more5_bridge_route",
            "more5_place_site_chest",
            "more5_carry_to_site",
            "more5_light_mine",
            "more5_craft_mine_torch",
            "more5_pickup_wood_drop",
            "more5_plant_sapling");
    private static final List<String> IMPLEMENTED = List.of(
            "more5_defend_player",
            "more5_feed_player",
            "more5_light_crops",
            "more5_light_saplings",
            "more5_water_crops",
            "more5_fertilize_growth",
            "more5_gather_fuel",
            "more5_make_charcoal",
            "more5_gather_build_material",
            "more5_craft_build_material",
            "more5_restore_block",
            "more5_bridge_route",
            "more5_place_site_chest",
            "more5_carry_to_site",
            "more5_light_mine",
            "more5_craft_mine_torch",
            "more5_pickup_wood_drop",
            "more5_plant_sapling");
    private static final List<String> STUBS = List.of();
    private static final List<String> ITEMS = List.of(
            "more5:player_guard",
            "more5:player_fed",
            "more5:growth_lit",
            "more5:growth_watered",
            "more5:growth_fed",
            "more5:fuel_ready",
            "more5:charcoal_ready",
            "more5:build_supply",
            "more5:block_restored",
            "more5:route_opened",
            "more5:site_chest",
            "more5:mine_lit",
            "more5:wood_drop",
            "more5:sapling_planted");

    @Test
    void more5Events_FragmentLoad_AreDeclaredWithTasks() {
        for (String event : EVENTS) {
            assertTrue(GRAPH.events().containsKey(event), "Событие входа области more5 объявлено: " + event);
            assertFalse(GRAPH.events().get(event).tasks().isEmpty(), "Событие ставит работу: " + event);
        }
    }

    @Test
    void more5Ways_FragmentLoad_AreWiredOrMarkedStub() {
        for (String way : WAYS) {
            SupplyGraph.Way known = GRAPH.way(way).orElseThrow();
            assertTrue(known.weight() > 0, "Стартовый вес пути положителен: " + way);
            assertEquals(
                    IMPLEMENTED.contains(way),
                    GRAPH.implemented(way),
                    "Путь " + way + " либо связан с действием, либо честно помечен заглушкой");
        }
        for (String stub : STUBS) {
            assertFalse(
                    GRAPH.implemented(stub), "Бессмысленное или дублирующее действие не выдаётся за готовое: " + stub);
        }
    }

    @Test
    void more5Events_FragmentLoad_TasksMatchWayState() {
        for (String event : EVENTS) {
            for (GraphEvent.Task task : GRAPH.events().get(event).tasks()) {
                assertEquals(
                        IMPLEMENTED.contains(task.id()),
                        task.implemented(),
                        "Задача события " + event + " -> " + task.id()
                                + " не расходится с состоянием пути: иначе граф ставит неисполнимую работу");
            }
        }
    }

    @Test
    void more5Items_FragmentLoad_HavePositiveStartWeights() {
        for (String item : ITEMS) {
            assertTrue(GRAPH.items().get(item).base() > 0, "Стартовый вес предмета положителен: " + item);
        }
    }

    @Test
    void more5FuelGroup_FragmentLoad_IsDeclared() {
        assertTrue(GRAPH.isGroup(More5Rules.FUEL), "Группа топлива области more5 объявлена");
        assertTrue(GRAPH.members(More5Rules.FUEL).contains("coal"), "В топливе есть уголь");
        assertTrue(GRAPH.members(More5Rules.FUEL).contains("charcoal"), "В топливе есть древесный уголь");
        assertTrue(GRAPH.members(More5Rules.FUEL).contains("oak_planks"), "В топливе есть доски");
    }

    @Test
    void more5Events_FragmentLoad_PushKnownNodes() {
        for (String event : EVENTS) {
            GraphEvent known = GRAPH.events().get(event);
            known.push()
                    .keySet()
                    .forEach(node -> assertTrue(
                            GRAPH.items().containsKey(node) || GRAPH.isGroup(node),
                            "Событие " + event + " толкает неизвестный узел: " + node));
            for (GraphEvent.Task task : known.tasks()) {
                task.consumes()
                        .keySet()
                        .forEach(node -> assertTrue(
                                GRAPH.items().containsKey(node) || GRAPH.isGroup(node),
                                "Задание " + task.id() + " требует неизвестный узел: " + node));
            }
        }
    }

    @Test
    void more5PlayerEvent_FragmentLoad_StaysWithinOrdinaryCeiling() {
        GraphEvent player = GRAPH.events().get("more5_player_weak");
        GraphPlan plan = GraphWorld.empty().event("more5_player_weak").plan();

        assertFalse(player.life(), "Событие игрока в бою — обычное, потолок 100 оставлен только жизни");
        assertTrue(
                player.tasks().stream().allMatch(task -> task.base() > 0), "У заданий защиты игрока есть вес события");
        assertTrue(
                plan.tasks().stream().allMatch(task -> task.priority() <= GraphPriorities.work(GRAPH)),
                "Задания обычного события не выходят за потолок 99: " + plan.tasks());
    }
}
