package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GraphStoreFarmFragmentsTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void storeFragment_StoreNodes_AreDeclared() {
        assertTrue(
                GRAPH.items().containsKey("food_reserve"),
                "Узел запаса еды объявлен во фрагменте склада"
                        + " Иначе тест storeFragment_StoreNodes_AreDeclared перестанет ловить эту поломку. При смене storeFragment_StoreNodes_AreDeclared проверь это утверждение.");
        assertTrue(
                GRAPH.items().containsKey("logistics"),
                "Узел логистики объявлен во фрагменте склада"
                        + " Иначе тест storeFragment_StoreNodes_AreDeclared перестанет ловить эту поломку. При смене storeFragment_StoreNodes_AreDeclared проверь это утверждение.");
        assertEquals(
                List.of("coal", "charcoal", "oak_planks", "oak_log"),
                GRAPH.groups().get("#fuel"),
                "Группа топлива объявлена один раз и в своём файле"
                        + " Иначе тест storeFragment_StoreNodes_AreDeclared перестанет ловить эту поломку. При смене storeFragment_StoreNodes_AreDeclared проверь это утверждение.");

        assertTrue(
                GRAPH.way("transfer_item").isPresent(),
                "Путь переноса объявлен"
                        + " Иначе тест storeFragment_StoreNodes_AreDeclared перестанет ловить эту поломку. При смене storeFragment_StoreNodes_AreDeclared проверь это утверждение.");
        assertTrue(
                GRAPH.way("sort_chest").isPresent(),
                "Путь сортировки объявлен"
                        + " Иначе тест storeFragment_StoreNodes_AreDeclared перестанет ловить эту поломку. При смене storeFragment_StoreNodes_AreDeclared проверь это утверждение.");
        assertTrue(
                GRAPH.implemented("transfer_item"),
                "Перенос исполняется действием склада"
                        + " Иначе тест storeFragment_StoreNodes_AreDeclared перестанет ловить эту поломку. При смене storeFragment_StoreNodes_AreDeclared проверь это утверждение.");
        assertTrue(
                GRAPH.implemented("sort_chest"),
                "Сортировка исполняется действием склада"
                        + " Иначе тест storeFragment_StoreNodes_AreDeclared перестанет ловить эту поломку. При смене storeFragment_StoreNodes_AreDeclared проверь это утверждение.");
        assertEquals(
                "food_reserve",
                GRAPH.way("restock_food").orElseThrow().makes(),
                "Пополнение еды закрывает недобор food_reserve"
                        + " Иначе тест storeFragment_StoreNodes_AreDeclared перестанет ловить эту поломку. При смене storeFragment_StoreNodes_AreDeclared проверь это утверждение.");
    }

    @Test
    void storeFragment_InputEvents_AreDeclared() {
        for (String event :
                List.of("store_minimum", "transfer_pending", "storage_mixed", "furnace_ready", "furnace_needs_fuel")) {
            assertTrue(
                    GRAPH.events().containsKey(event),
                    "Событие входа склада объявлено: " + event
                            + " Иначе тест storeFragment_InputEvents_AreDeclared перестанет ловить эту поломку. При смене storeFragment_InputEvents_AreDeclared проверь это утверждение.");
        }
        GraphEvent ready = GRAPH.events().get("furnace_ready");
        assertEquals(
                "take_smelted",
                ready.tasks().get(0).id(),
                "Забрать готовое — отдельная работа печи"
                        + " Иначе тест storeFragment_InputEvents_AreDeclared перестанет ловить эту поломку. При смене storeFragment_InputEvents_AreDeclared проверь это утверждение.");
        GraphEvent fuel = GRAPH.events().get("furnace_needs_fuel");
        assertEquals(
                "load_furnace",
                fuel.tasks().get(0).id(),
                "Положить в печь — отдельная работа печи"
                        + " Иначе тест storeFragment_InputEvents_AreDeclared перестанет ловить эту поломку. При смене storeFragment_InputEvents_AreDeclared проверь это утверждение.");
        assertTrue(
                fuel.tasks().get(0).consumes().containsKey("#fuel"),
                "Загрузка печи расходует топливо"
                        + " Иначе тест storeFragment_InputEvents_AreDeclared перестанет ловить эту поломку. При смене storeFragment_InputEvents_AreDeclared проверь это утверждение.");
    }

    @Test
    void farmFragment_FarmNodes_AreDeclared() {
        assertTrue(
                GRAPH.items().containsKey("farmland"),
                "Узел пашни объявлен во фрагменте фермы"
                        + " Иначе тест farmFragment_FarmNodes_AreDeclared перестанет ловить эту поломку. При смене farmFragment_FarmNodes_AreDeclared проверь это утверждение.");
        assertTrue(
                GRAPH.items().containsKey("herd_fed"),
                "Узел кормления стада объявлен"
                        + " Иначе тест farmFragment_FarmNodes_AreDeclared перестанет ловить эту поломку. При смене farmFragment_FarmNodes_AreDeclared проверь это утверждение.");
        assertEquals(
                List.of("wheat", "carrot", "potato", "beetroot"),
                GRAPH.groups().get("#crops"),
                "Группа урожая объявлена во фрагменте фермы"
                        + " Иначе тест farmFragment_FarmNodes_AreDeclared перестанет ловить эту поломку. При смене farmFragment_FarmNodes_AreDeclared проверь это утверждение.");

        SupplyGraph.Building field = GRAPH.buildings().get("wheat_field");
        assertTrue(
                field != null && field.stage() == 1 && field.optional(),
                "Грядка пшеницы этапа 1 — необязательная постройка, чтобы не требовать её каждый цикл"
                        + " Иначе тест farmFragment_FarmNodes_AreDeclared перестанет ловить эту поломку. При смене farmFragment_FarmNodes_AreDeclared проверь это утверждение.");
        assertTrue(
                GRAPH.implemented("wheat_field"),
                "Грядка строится действием build_farm"
                        + " Иначе тест farmFragment_FarmNodes_AreDeclared перестанет ловить эту поломку. При смене farmFragment_FarmNodes_AreDeclared проверь это утверждение.");

        assertEquals(
                "wheat_field",
                GRAPH.way("till_soil").orElseThrow().building(),
                "Вспашка обслуживает уже существующую грядку: без building граф требует farmland до стройки "
                        + "и посылает духа к сухой земле у дома, иначе продовольственная цепочка встаёт. "
                        + "При смене farmFragment_FarmNodes_AreDeclared проверь "
                        + "FarmInputs.field и живой wheat_field.");
        assertEquals(
                "wheat_field",
                GRAPH.way("plant_crop").orElseThrow().building(),
                "Посев привязан к грядке пшеницы"
                        + " Иначе тест farmFragment_FarmNodes_AreDeclared перестанет ловить эту поломку. При смене farmFragment_FarmNodes_AreDeclared проверь это утверждение.");
        assertTrue(
                GRAPH.way("feed_animals").orElseThrow().consumes().containsKey("#crops"),
                "Кормление стада расходует урожай"
                        + " Иначе тест farmFragment_FarmNodes_AreDeclared перестанет ловить эту поломку. При смене farmFragment_FarmNodes_AreDeclared проверь это утверждение.");
        assertTrue(
                GRAPH.way("lure_animal").orElseThrow().consumes().containsKey("wheat"),
                "Приманка животного расходует пшеницу"
                        + " Иначе тест farmFragment_FarmNodes_AreDeclared перестанет ловить эту поломку. При смене farmFragment_FarmNodes_AreDeclared проверь это утверждение.");
        assertTrue(
                GRAPH.way("milk_animal").orElseThrow().needs().containsKey("herd_cow"),
                "Дойка требует живое стадо"
                        + " Иначе тест farmFragment_FarmNodes_AreDeclared перестанет ловить эту поломку. При смене farmFragment_FarmNodes_AreDeclared проверь это утверждение.");
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
                        + "даже если семян ещё нет — их добудет граф: " + plan.tasks()
                        + " Иначе тест farmFragment_DirtWithoutSeeds_StillPlansWheatFieldAndSeedGathering перестанет ловить эту поломку. При смене farmFragment_DirtWithoutSeeds_StillPlansWheatFieldAndSeedGathering проверь это утверждение.");
        assertTrue(
                plan.tasks().stream().anyMatch(task -> task.id().startsWith("gather_seeds")),
                "Нехватку семян грядка заказывает через граф, а не ждёт их случайно: " + plan.tasks()
                        + " Иначе тест farmFragment_DirtWithoutSeeds_StillPlansWheatFieldAndSeedGathering перестанет ловить эту поломку. При смене farmFragment_DirtWithoutSeeds_StillPlansWheatFieldAndSeedGathering проверь это утверждение.");
    }

    @Test
    void farmFragment_PlannedWheatFieldWithoutMaterials_IsStillRequested() {
        GraphPlan plan = GraphWorld.settled()
                .built("wheat_field", GraphState.Built.PLANNED)
                .with("dirt", 0)
                .with("wheat_seeds", 0)
                .plan();

        assertTrue(
                plan.tasks().stream().anyMatch(task -> "building:wheat_field".equals(task.id())),
                "Грядка этапа 1 в плане поселения обязана строиться и без остатка земли в запасе: иначе улицы "
                        + "съедают всю землю, а грядка не заказывается никогда (obsrest B11): " + plan.tasks()
                        + " При смене farmFragment_PlannedWheatFieldWithoutMaterials_IsStillRequested проверь это утверждение.");
        assertTrue(
                plan.tasks().stream().anyMatch(task -> task.id().startsWith("dig_dirt")),
                "Нехватку земли для грядки граф добывает сам, а не ждёт случайного остатка: " + plan.tasks()
                        + " Иначе тест farmFragment_PlannedWheatFieldWithoutMaterials_IsStillRequested перестанет ловить эту поломку. При смене farmFragment_PlannedWheatFieldWithoutMaterials_IsStillRequested проверь это утверждение.");
        assertTrue(
                plan.tasks().stream().anyMatch(task -> task.id().startsWith("gather_seeds")),
                "Нехватку семян для грядки граф добывает сам, а не ждёт случайного остатка: " + plan.tasks()
                        + " Иначе тест farmFragment_PlannedWheatFieldWithoutMaterials_IsStillRequested перестанет ловить эту поломку. При смене farmFragment_PlannedWheatFieldWithoutMaterials_IsStillRequested проверь это утверждение.");
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
                        + result.post().stream().map(GraphTickets.Ticket::id).toList()
                        + " Иначе тест farmFragment_DirtWithoutSeeds_PostsWheatFieldTicket перестанет ловить эту поломку. При смене farmFragment_DirtWithoutSeeds_PostsWheatFieldTicket проверь это утверждение.");
    }

    @Test
    void farmFragment_FarmEvents_AreDeclared() {
        for (String event :
                List.of("crop_ready", "crop_dry", "field_empty", "animal_starving", "pen_full", "orchard_empty")) {
            assertTrue(
                    GRAPH.events().containsKey(event),
                    "Событие входа фермы объявлено: " + event
                            + " Иначе тест farmFragment_FarmEvents_AreDeclared перестанет ловить эту поломку. При смене farmFragment_FarmEvents_AreDeclared проверь это утверждение.");
        }
        GraphEvent orchard = GRAPH.events().get("orchard_empty");
        assertEquals(
                "replant_orchard",
                orchard.tasks().get(0).id(),
                "После рубки в саду — отдельная посадка"
                        + " Иначе тест farmFragment_FarmEvents_AreDeclared перестанет ловить эту поломку. При смене farmFragment_FarmEvents_AreDeclared проверь это утверждение.");
        assertNull(
                orchard.tasks().get(0).building(),
                "Посадка не ждёт готовой постройки сада: клетка сада свободна — саженец ставится сразу"
                        + " Иначе тест farmFragment_FarmEvents_AreDeclared перестанет ловить эту поломку. При смене farmFragment_FarmEvents_AreDeclared проверь это утверждение.");
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
            assertTrue(
                    GRAPH.way(way).isPresent(),
                    "Путь объявлен в данных, вес проверяет SupplyGraph.Way: " + way
                            + " Иначе тест newNodes_StartWeights_ArePositive перестанет ловить эту поломку. При смене newNodes_StartWeights_ArePositive проверь это утверждение.");
        }
        for (String building : List.of("warehouse", "wheat_field")) {
            assertTrue(
                    GRAPH.buildings().get(building).base() > 0,
                    "Стартовый вес постройки положителен: " + building
                            + " Иначе тест newNodes_StartWeights_ArePositive перестанет ловить эту поломку. При смене newNodes_StartWeights_ArePositive проверь это утверждение.");
        }
        for (String item : List.of("food_reserve", "logistics", "farmland", "herd_fed")) {
            assertTrue(
                    GRAPH.items().get(item).base() > 0,
                    "Стартовый вес предмета положителен: " + item
                            + " Иначе тест newNodes_StartWeights_ArePositive перестанет ловить эту поломку. При смене newNodes_StartWeights_ArePositive проверь это утверждение.");
        }
    }
}
