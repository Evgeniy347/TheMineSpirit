package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StoreRulesTests {
    @Test
    void config_FragmentRead_ReturnsMinimums() {
        StoreRules.Config config = StoreRules.config();

        assertEquals(25, config.reservePercent(), "Резерв минимума задаётся данными");
        assertEquals(64, config.minimums().get("#logs"), "Минимум брёвен берётся из настроек");
        assertEquals(128, config.minimums().get("cobblestone"), "Минимум булыжника берётся из настроек");
        assertEquals(16, config.minimums().get("bread"), "Минимум хлеба берётся из настроек");
        assertEquals("iron", config.toolMinimums().get("pickaxe"), "Железная кирка нужна к этапу 2");
        assertEquals(6, config.maxItemTypesPerChest(), "Порог «смешанного» сундука задаётся данными");
    }

    @Test
    void shortfalls_MinimumPlusReserve_CountsRealAmount() {
        Map<String, Integer> shortfall = StoreRules.shortfalls(
                Map.of("#logs", 64, "cobblestone", 128), Map.of("#logs", 40, "cobblestone", 200), 25);

        assertEquals(Map.of("#logs", 40), shortfall, "Нужно 80 брёвен, есть 40 — недобор 40");
    }

    @Test
    void shortfalls_StockAboveMinimum_IsEmpty() {
        assertEquals(
                Map.of(),
                StoreRules.shortfalls(Map.of("bread", 16), Map.of("bread", 20), 25),
                "Запас выше минимума с резервом не даёт недобора");
    }

    @Test
    void groupCounts_LogGroup_SumsMembers() {
        SupplyGraph graph = SupplyGraph.loadDefault();
        Map<String, Integer> actual = Map.of("oak_log", 10, "birch_log", 5, "cobblestone", 7);

        assertEquals(
                Map.of("#logs", 15),
                StoreRules.groupCounts(graph, actual, List.of("#logs")),
                "Счёт группы — сумма предметов, а не одного вида");
    }

    @Test
    void actual_ChestsAndSpirits_UseShortIds() {
        List<GraphFacts.Chest> chests = List.of(new GraphFacts.Chest(
                new GraphFacts.Position(1, 64, 2), Map.of("minecraft:oak_log", 5, "minecraft:coal", 2), 3, 27));
        GraphFacts.Spirit spirit = new GraphFacts.Spirit(
                "spirit-1",
                new GraphFacts.Position(0, 64, 0),
                20,
                18,
                300,
                false,
                false,
                false,
                null,
                Map.of("minecraft:oak_log", 3),
                List.of(),
                List.of());

        Map<String, Integer> actual = StoreRules.actual(chests, Map.of("spirit-1", spirit));

        assertEquals(8, actual.get("oak_log"), "Реальное количество — сундуки плюс несомое");
        assertEquals(2, actual.get("coal"), "Содержимое сундука переводится в короткое имя графа");
    }

    @Test
    void mixedChests_ManyTypes_AreCounted() {
        List<GraphFacts.Chest> chests = List.of(
                new GraphFacts.Chest(
                        new GraphFacts.Position(0, 64, 0),
                        Map.of("oak_log", 1, "coal", 1, "bread", 1, "wheat", 1),
                        20,
                        27),
                new GraphFacts.Chest(new GraphFacts.Position(1, 64, 0), Map.of("oak_log", 1, "coal", 1), 25, 27));

        assertEquals(1, StoreRules.mixedChests(chests, 4), "Смешанным считается сундук, где типов не меньше порога");
    }

    @Test
    void events_LowMinimumAndStorage_AddsAll() {
        Set<String> events =
                StoreRules.events(Set.of("night"), new StoreRules.Signals(true, true, true, true, true, true));

        assertTrue(events.contains("night"), "Старые события не теряются");
        assertTrue(events.contains("store_minimum"), "Недобор минимума — событие входа");
        assertTrue(events.contains("storage_full"), "Мало места — событие склада");
        assertTrue(events.contains("storage_mixed"), "Смешанный сундук — событие сортировки");
        assertTrue(events.contains("transfer_pending"), "Несомое — событие переноса");
        assertTrue(events.contains("furnace_ready"), "Готовый выход печи — событие забрать");
        assertTrue(events.contains("furnace_needs_fuel"), "Печь без топлива — событие загрузить");
    }
}
