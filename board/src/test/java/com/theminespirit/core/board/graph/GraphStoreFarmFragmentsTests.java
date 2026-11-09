package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GraphStoreFarmFragmentsTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void storeFragment_StoreNodes_AreDeclared() {
        assertTrue(GRAPH.items().containsKey("food_reserve"), "Узел запаса еды объявлен во фрагменте склада");
        assertTrue(GRAPH.items().containsKey("logistics"), "Узел логистики объявлен во фрагменте склада");
        assertEquals(
                List.of("coal", "charcoal", "oak_planks", "oak_log"),
                GRAPH.groups().get("#fuel"),
                "Группа топлива объявлена один раз и в своём файле");

        SupplyGraph.Building storage = GRAPH.buildings().get("storage_space");
        assertTrue(storage != null && storage.optional(), "Новая пара сундуков — необязательная постройка");
        assertTrue(GRAPH.implemented("storage_space"), "Новая пара сундуков исполняется узлом STOCK_CHESTS");

        assertTrue(GRAPH.way("transfer_item").isPresent(), "Путь переноса объявлен");
        assertTrue(GRAPH.way("sort_chest").isPresent(), "Путь сортировки объявлен");
        assertTrue(GRAPH.implemented("transfer_item"), "Перенос исполняется действием склада");
        assertTrue(GRAPH.implemented("sort_chest"), "Сортировка исполняется действием склада");
        assertEquals(
                "food_reserve",
                GRAPH.way("restock_food").orElseThrow().makes(),
                "Пополнение еды закрывает недобор food_reserve");
    }

    @Test
    void storeFragment_InputEvents_AreDeclared() {
        for (String event : List.of(
                "store_minimum",
                "transfer_pending",
                "storage_full",
                "storage_mixed",
                "furnace_ready",
                "furnace_needs_fuel")) {
            assertTrue(GRAPH.events().containsKey(event), "Событие входа склада объявлено: " + event);
        }
        GraphEvent ready = GRAPH.events().get("furnace_ready");
        assertEquals("take_smelted", ready.tasks().get(0).id(), "Забрать готовое — отдельная работа печи");
        GraphEvent fuel = GRAPH.events().get("furnace_needs_fuel");
        assertEquals("load_furnace", fuel.tasks().get(0).id(), "Положить в печь — отдельная работа печи");
        assertTrue(fuel.tasks().get(0).consumes().containsKey("#fuel"), "Загрузка печи расходует топливо");
    }

    @Test
    void farmFragment_FarmNodes_AreDeclared() {
        assertTrue(GRAPH.items().containsKey("farmland"), "Узел пашни объявлен во фрагменте фермы");
        assertTrue(GRAPH.items().containsKey("herd_fed"), "Узел кормления стада объявлен");
        assertEquals(
                List.of("wheat", "carrot", "potato", "beetroot"),
                GRAPH.groups().get("#crops"),
                "Группа урожая объявлена во фрагменте фермы");

        SupplyGraph.Building field = GRAPH.buildings().get("wheat_field");
        assertTrue(
                field != null && field.stage() == 1 && field.optional(),
                "Грядка пшеницы этапа 1 — необязательная постройка, чтобы не требовать её каждый цикл");
        assertTrue(GRAPH.implemented("wheat_field"), "Грядка строится действием build_farm");

        assertEquals(
                "wheat_field", GRAPH.way("plant_crop").orElseThrow().building(), "Посев привязан к грядке пшеницы");
        assertTrue(
                GRAPH.way("feed_animals").orElseThrow().consumes().containsKey("#crops"),
                "Кормление стада расходует урожай");
        assertTrue(
                GRAPH.way("lure_animal").orElseThrow().consumes().containsKey("wheat"),
                "Приманка животного расходует пшеницу");
        assertTrue(GRAPH.way("milk_animal").orElseThrow().needs().containsKey("herd_cow"), "Дойка требует живое стадо");
    }

    @Test
    void farmFragment_DirtWithoutSeeds_StillPlansWheatFieldAndSeedGathering() {
        GraphPlan plan = GraphWorld.settled()
                .built("wheat_field", GraphState.Built.PLANNED)
                .with("dirt", 8)
                .with("wheat_seeds", 0)
                .plan();

        assertTrue(
                plan.tasks().stream().anyMatch(task -> "building:wheat_field".equals(task.id())),
                "Грядка этапа 1 не строится сама собой: при наличии земли она становится заданием, "
                        + "даже если семян ещё нет — их добудет граф: " + plan.tasks());
        assertTrue(
                plan.tasks().stream().anyMatch(task -> task.id().startsWith("gather_seeds")),
                "Нехватку семян грядка заказывает через граф, а не ждёт их случайно: " + plan.tasks());
    }

    @Test
    void farmFragment_DirtWithoutSeeds_PostsWheatFieldTicket() {
        GraphWorld world = GraphWorld.settled()
                .built("wheat_field", GraphState.Built.PLANNED)
                .with("dirt", 8)
                .with("wheat_seeds", 0);
        GraphState state = world.state();
        GraphTickets.Result result = GraphTickets.of(GRAPH, state, GraphPlanner.plan(GRAPH, state));

        assertTrue(
                result.post().stream().anyMatch(ticket -> "graph:building:wheat_field".equals(ticket.id())),
                "Грядка этапа 1 с землёй в запасе обязана стать билетом доски: PLANNED без задания — "
                        + "сад не строится (observe25 B11): "
                        + result.post().stream().map(GraphTickets.Ticket::id).toList());
    }

    @Test
    void farmFragment_FarmEvents_AreDeclared() {
        for (String event :
                List.of("crop_ready", "crop_dry", "field_empty", "animal_starving", "pen_full", "orchard_empty")) {
            assertTrue(GRAPH.events().containsKey(event), "Событие входа фермы объявлено: " + event);
        }
        GraphEvent orchard = GRAPH.events().get("orchard_empty");
        assertEquals("replant_orchard", orchard.tasks().get(0).id(), "После рубки в саду — отдельная посадка");
        assertEquals("orchard_oak", orchard.tasks().get(0).building(), "Посадка идёт в сад");
    }

    @Test
    void newNodes_StartWeights_ArePositive() {
        for (String way : List.of(
                "transfer_item",
                "sort_chest",
                "restock_food",
                "till_soil",
                "plant_crop",
                "harvest_crop",
                "feed_animals",
                "lure_animal",
                "milk_animal")) {
            assertTrue(GRAPH.way(way).orElseThrow().weight() > 0, "Стартовый вес пути положителен: " + way);
        }
        for (String building : List.of("storage_space", "wheat_field")) {
            assertTrue(GRAPH.buildings().get(building).base() > 0, "Стартовый вес постройки положителен: " + building);
        }
        for (String item : List.of("food_reserve", "logistics", "farmland", "herd_fed")) {
            assertTrue(GRAPH.items().get(item).base() > 0, "Стартовый вес предмета положителен: " + item);
        }
    }
}
