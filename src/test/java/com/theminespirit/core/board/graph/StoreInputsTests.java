package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StoreInputsTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();
    private static final Set<String> FOODS = Set.of("minecraft:bread", "minecraft:cooked_beef");

    @Test
    void settings_ProductionFile_HasConfiguredFloorAndReserve() {
        StoreInputs.Settings settings = StoreInputs.settings();

        assertEquals(80, settings.minimums().get("#logs").required(), "Минимум брёвен — 64 плюс резерв 16");
        assertEquals(160, settings.minimums().get("cobblestone").required(), "Минимум булыжника — 128 плюс резерв 32");
        assertEquals(24, settings.minimums().get("food").required(), "Минимум еды — 16 плюс резерв 8");
        assertEquals(20, settings.minFreePercent(), "Свободного места на складе обязано быть не меньше 20 %");
    }

    @Test
    void stageReqs_ChestContents_CountsGroupsFoodAndTools() {
        GraphFacts.StageReqs reqs = StoreInputs.stageReqs(
                GRAPH,
                List.of(chest(
                        Map.of(
                                "minecraft:oak_log",
                                10,
                                "minecraft:cobblestone",
                                20,
                                "minecraft:bread",
                                3,
                                "minecraft:iron_pickaxe",
                                1),
                        10,
                        27)),
                FOODS,
                settings(Map.of(
                        "#logs",
                        new StoreInputs.Minimum(64, 16),
                        "cobblestone",
                        new StoreInputs.Minimum(128, 32),
                        "food",
                        new StoreInputs.Minimum(16, 8),
                        "iron_pickaxe",
                        new StoreInputs.Minimum(1, 0))));

        assertEquals(80, reqs.minimum().get("#logs"), "Минимум группы складывается из настройки и резерва");
        assertEquals(10, reqs.actual().get("#logs"), "Факт группы — сумма всех брёвен в сундуке");
        assertEquals(3, reqs.actual().get("food"), "Факт еды — все съестное из сундука");
        assertEquals(1, reqs.actual().get("iron_pickaxe"), "Железная кирка из сундука закрывает минимум");
    }

    @Test
    void stageReqs_NoChests_ActualIsZero() {
        GraphFacts.StageReqs reqs = StoreInputs.stageReqs(
                GRAPH, List.of(), FOODS, settings(Map.of("cobblestone", new StoreInputs.Minimum(128, 32))));

        assertEquals(0, reqs.actual().get("cobblestone"), "Пустой склад не закрывает минимум");
    }

    @Test
    void stageReqs_StonePickaxe_DoesNotCoverIronMinimum() {
        GraphFacts.StageReqs reqs = StoreInputs.stageReqs(
                GRAPH,
                List.of(chest(Map.of("minecraft:stone_pickaxe", 1), 20, 27)),
                FOODS,
                settings(Map.of("iron_pickaxe", new StoreInputs.Minimum(1, 0))));

        assertEquals(0, reqs.actual().get("iron_pickaxe"), "Каменная кирка не заменяет железную в минимуме");
    }

    @Test
    void stageReqs_UnknownNode_Throws() {
        StoreInputs.Settings settings = settings(Map.of("unknown_item", new StoreInputs.Minimum(1, 0)));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> StoreInputs.stageReqs(GRAPH, List.of(), FOODS, settings),
                "Опечатка в настройках склада обязана ронять сборку входа, а не молча терять минимум");

        assertTrue(
                error.getMessage().contains("unknown_item"),
                "Ошибка обязана назвать неизвестный узел: " + error.getMessage());
    }

    @Test
    void events_DeficitMinimum_AddsStoreMinimum() {
        Set<String> events = StoreInputs.events(facts(
                List.of(),
                Map.of(),
                List.of(),
                new GraphFacts.StageReqs(Map.of("cobblestone", 160), Map.of("cobblestone", 10))));

        assertTrue(events.contains(StoreInputs.MINIMUM), "Недобор минимума обязан поднять узел store_minimum");
    }

    @Test
    void events_FullMinimum_NoStoreMinimum() {
        Set<String> events = StoreInputs.events(facts(
                List.of(),
                Map.of(),
                List.of(),
                new GraphFacts.StageReqs(Map.of("cobblestone", 160), Map.of("cobblestone", 160))));

        assertFalse(events.contains(StoreInputs.MINIMUM), "Полный минимум не поднимает закупку");
    }

    @Test
    void events_CarriedSurplusAndRoom_AddsTransfer() {
        GraphFacts.Spirit carrier = spirit(Map.of("minecraft:oak_log", 12));
        Set<String> events = StoreInputs.events(facts(
                List.of(chest(Map.of(), 10, 27)),
                Map.of(),
                List.of(carrier),
                new GraphFacts.StageReqs(Map.of(), Map.of())));

        assertTrue(
                events.contains(StoreInputs.TRANSFER),
                "Дух с лишними вещами и свободный сундук обязаны поднять перенос");
    }

    @Test
    void events_FullChests_NoTransfer() {
        GraphFacts.Spirit carrier = spirit(Map.of("minecraft:oak_log", 12));
        Set<String> events = StoreInputs.events(facts(
                List.of(chest(Map.of(), 0, 27)),
                Map.of(),
                List.of(carrier),
                new GraphFacts.StageReqs(Map.of(), Map.of())));

        assertFalse(events.contains(StoreInputs.TRANSFER), "В полный сундук нечего переносить");
    }

    @Test
    void events_FurnaceWithOutput_AddsSmeltTake() {
        Set<String> events = StoreInputs.events(facts(
                List.of(),
                Map.of("furnace", station("furnace", null, null, "output")),
                List.of(),
                new GraphFacts.StageReqs(Map.of(), Map.of())));

        assertTrue(events.contains(StoreInputs.SMELT_TAKE), "Готовый выход печи обязан поднять «забрать»");
    }

    @Test
    void events_EmptyFurnaceWithFuel_AddsSmeltPut() {
        Set<String> events = StoreInputs.events(facts(
                List.of(chest(Map.of("minecraft:coal", 4), 10, 27)),
                Map.of("furnace", station("furnace", null, null, null)),
                List.of(),
                new GraphFacts.StageReqs(Map.of(), Map.of())));

        assertTrue(
                events.contains(StoreInputs.SMELT_PUT),
                "Пустая печь и топливо в сундуке обязаны поднять «положить и уйти»");
    }

    @Test
    void events_NearlyFullChests_AddsStorageSpace() {
        Set<String> events = StoreInputs.events(facts(
                List.of(chest(Map.of("minecraft:cobblestone", 27), 1, 10)),
                Map.of(),
                List.of(),
                new GraphFacts.StageReqs(Map.of(), Map.of())));

        assertTrue(
                events.contains(StoreInputs.STORAGE_SPACE), "Меньше 20 % свободного места требует новую пару сундуков");
    }

    @Test
    void events_MixedChests_AddSort() {
        Map<String, Integer> mixed = Map.of(
                "minecraft:oak_log", 1,
                "minecraft:cobblestone", 1,
                "minecraft:coal", 1,
                "minecraft:bread", 1,
                "minecraft:torch", 1,
                "minecraft:stick", 1,
                "minecraft:iron_ingot", 1);
        Set<String> events = StoreInputs.events(facts(
                List.of(chest(mixed, 5, 27), chest(Map.of(), 10, 27)),
                Map.of(),
                List.of(),
                new GraphFacts.StageReqs(Map.of(), Map.of())));

        assertTrue(events.contains(StoreInputs.SORT), "Смешанный сундук при втором сундуке требует сортировки");
    }

    @Test
    void events_CompactChests_NoSort() {
        Set<String> events = StoreInputs.events(facts(
                List.of(chest(Map.of("minecraft:oak_log", 4, "minecraft:stick", 4), 5, 27), chest(Map.of(), 10, 27)),
                Map.of(),
                List.of(),
                new GraphFacts.StageReqs(Map.of(), Map.of())));

        assertFalse(events.contains(StoreInputs.SORT), "Два вида вещей в сундуке сортировки не требуют");
    }

    private static StoreInputs.Settings settings(Map<String, StoreInputs.Minimum> minimums) {
        return new StoreInputs.Settings(minimums, 20, 8, 6);
    }

    private static GraphFacts.Chest chest(Map<String, Integer> contents, int free, int total) {
        return new GraphFacts.Chest(new GraphFacts.Position(0, 64, 0), contents, free, total);
    }

    private static GraphFacts.Station station(String id, String fuel, String input, String output) {
        return new GraphFacts.Station(id, id, new GraphFacts.Position(1, 64, 1), false, fuel, input, output);
    }

    private static GraphFacts.Spirit spirit(Map<String, Integer> inventory) {
        return new GraphFacts.Spirit(
                "spirit-1",
                new GraphFacts.Position(0, 64, 0),
                20,
                18,
                300,
                false,
                false,
                false,
                null,
                inventory,
                List.of(),
                List.of());
    }

    private static GraphFacts facts(
            List<GraphFacts.Chest> chests,
            Map<String, GraphFacts.Station> stations,
            List<GraphFacts.Spirit> spirits,
            GraphFacts.StageReqs reqs) {
        Map<String, GraphFacts.Spirit> byId = new LinkedHashMap<>();
        spirits.forEach(spirit -> byId.put(spirit.id(), spirit));
        return new GraphFacts(
                byId, List.of(), chests, List.of(), stations, Map.of(), List.of(), GraphFacts.Terrain.empty(), reqs);
    }
}
