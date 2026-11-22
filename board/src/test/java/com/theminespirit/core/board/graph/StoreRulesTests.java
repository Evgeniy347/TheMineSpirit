package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StoreRulesTests {
    @Test
    void config_FragmentRead_ReturnsMinimums() {
        StoreRules.Config config = StoreRules.config();

        assertEquals(
                25,
                config.reservePercent(),
                "Резерв минимума задаётся данными"
                        + " Иначе тест config_FragmentRead_ReturnsMinimums перестанет ловить эту поломку. При смене config_FragmentRead_ReturnsMinimums проверь это утверждение.");
        assertEquals(
                64,
                config.minimums().get("#logs"),
                "Минимум брёвен берётся из настроек"
                        + " Иначе тест config_FragmentRead_ReturnsMinimums перестанет ловить эту поломку. При смене config_FragmentRead_ReturnsMinimums проверь это утверждение.");
        assertEquals(
                128,
                config.minimums().get("cobblestone"),
                "Минимум булыжника берётся из настроек"
                        + " Иначе тест config_FragmentRead_ReturnsMinimums перестанет ловить эту поломку. При смене config_FragmentRead_ReturnsMinimums проверь это утверждение.");
        assertEquals(
                16,
                config.minimums().get("bread"),
                "Минимум хлеба берётся из настроек"
                        + " Иначе тест config_FragmentRead_ReturnsMinimums перестанет ловить эту поломку. При смене config_FragmentRead_ReturnsMinimums проверь это утверждение.");
        assertEquals(
                "iron",
                config.toolMinimums().get("pickaxe"),
                "Железная кирка нужна к этапу 2"
                        + " Иначе тест config_FragmentRead_ReturnsMinimums перестанет ловить эту поломку. При смене config_FragmentRead_ReturnsMinimums проверь это утверждение.");
        assertEquals(
                6,
                config.maxItemTypesPerChest(),
                "Порог «смешанного» сундука задаётся данными"
                        + " Иначе тест config_FragmentRead_ReturnsMinimums перестанет ловить эту поломку. При смене config_FragmentRead_ReturnsMinimums проверь это утверждение.");
    }

    @Test
    void shortfalls_MinimumPlusReserve_CountsRealAmount() {
        Map<String, Integer> shortfall = StoreRules.shortfalls(
                Map.of("#logs", 64, "cobblestone", 128), Map.of("#logs", 40, "cobblestone", 200), 25);

        assertEquals(
                Map.of("#logs", 40),
                shortfall,
                "Нужно 80 брёвен, есть 40 — недобор 40"
                        + " Иначе тест shortfalls_MinimumPlusReserve_CountsRealAmount перестанет ловить эту поломку. При смене shortfalls_MinimumPlusReserve_CountsRealAmount проверь это утверждение.");
    }

    @Test
    void shortfalls_StockAboveMinimum_IsEmpty() {
        assertEquals(
                Map.of(),
                StoreRules.shortfalls(Map.of("bread", 16), Map.of("bread", 20), 25),
                "Запас выше минимума с резервом не даёт недобора"
                        + " Иначе тест shortfalls_StockAboveMinimum_IsEmpty перестанет ловить эту поломку. При смене shortfalls_StockAboveMinimum_IsEmpty проверь это утверждение.");
    }

    @Test
    void groupCounts_LogGroup_SumsMembers() {
        SupplyGraph graph = SupplyGraph.loadDefault();
        Map<String, Integer> actual = Map.of("oak_log", 10, "birch_log", 5, "cobblestone", 7);

        assertEquals(
                Map.of("#logs", 15),
                StoreRules.groupCounts(graph, actual, List.of("#logs")),
                "Счёт группы — сумма предметов, а не одного вида"
                        + " Иначе тест groupCounts_LogGroup_SumsMembers перестанет ловить эту поломку. При смене groupCounts_LogGroup_SumsMembers проверь это утверждение.");
    }

    @Test
    void actual_ChestsAndSpirits_UseShortIds() {
        List<GraphFacts.Chest> chests = List.of(new GraphFacts.Chest(
                new GraphFacts.Position(1, 64, 2),
                Map.of("minecraft:oak_log", 5, "minecraft:coal", 2),
                3,
                27,
                GraphFacts.Reach.UNKNOWN));
        GraphFacts.Spirit spirit = new GraphFacts.Spirit(
                "spirit-1",
                new GraphFacts.Position(0, 64, 0),
                20,
                Math.max(20, 1f),
                null,
                18,
                300,
                false,
                false,
                false,
                null,
                Map.of("minecraft:oak_log", 3),
                List.of(),
                List.of(),
                null);

        Map<String, Integer> actual = StoreRules.actual(chests, Map.of("spirit-1", spirit));

        assertEquals(
                8,
                actual.get("oak_log"),
                "Реальное количество — сундуки плюс несомое"
                        + " Иначе тест actual_ChestsAndSpirits_UseShortIds перестанет ловить эту поломку. При смене actual_ChestsAndSpirits_UseShortIds проверь это утверждение.");
        assertEquals(
                2,
                actual.get("coal"),
                "Содержимое сундука переводится в короткое имя графа"
                        + " Иначе тест actual_ChestsAndSpirits_UseShortIds перестанет ловить эту поломку. При смене actual_ChestsAndSpirits_UseShortIds проверь это утверждение.");
    }

    @Test
    void mixedChests_ManyTypes_AreCounted() {
        List<GraphFacts.Chest> chests = List.of(
                new GraphFacts.Chest(
                        new GraphFacts.Position(0, 64, 0),
                        Map.of("oak_log", 1, "coal", 1, "bread", 1, "wheat", 1),
                        20,
                        27,
                        GraphFacts.Reach.UNKNOWN),
                new GraphFacts.Chest(
                        new GraphFacts.Position(1, 64, 0),
                        Map.of("oak_log", 1, "coal", 1),
                        25,
                        27,
                        GraphFacts.Reach.UNKNOWN));

        assertEquals(
                1,
                StoreRules.mixedChests(chests, 4),
                "Смешанным считается сундук, где типов не меньше порога"
                        + " Иначе тест mixedChests_ManyTypes_AreCounted перестанет ловить эту поломку. При смене mixedChests_ManyTypes_AreCounted проверь это утверждение.");
    }

    @Test
    void sortTarget_ExtraChestFirst_TakesPlannedChest() {
        GraphFacts.Chest extra = new GraphFacts.Chest(
                new GraphFacts.Position(-177, 69, -477), Map.of("dirt", 3), 20, 27, GraphFacts.Reach.PATH);
        GraphFacts.Chest planned = new GraphFacts.Chest(
                new GraphFacts.Position(-178, 68, -518), Map.of("dirt", 3), 20, 27, GraphFacts.Reach.PATH);

        assertEquals(
                planned,
                StoreRules.sortTarget(List.of(extra, planned), Set.of(new BlockCoord(-178, 68, -518)))
                        .orElseThrow(),
                "Сундук вне плана не сортируют: его разбирают и увозят вещи, а сортировка — только у склада поселения"
                        + " Иначе тест sortTarget_ExtraChestFirst_TakesPlannedChest перестанет ловить эту поломку. При смене sortTarget_ExtraChestFirst_TakesPlannedChest проверь это утверждение.");
    }

    @Test
    void sortTarget_PlannedChestUnreachable_SkipsIt() {
        GraphFacts.Chest walled = new GraphFacts.Chest(
                new GraphFacts.Position(-178, 68, -518), Map.of("dirt", 3), 20, 27, GraphFacts.Reach.NO_PATH);

        assertTrue(
                StoreRules.sortTarget(List.of(walled), Set.of(new BlockCoord(-178, 68, -518)))
                        .isEmpty(),
                "К плановому сундуку без пути не посылают: дух застревает на подходе и проваливает задание"
                        + " Иначе тест sortTarget_PlannedChestUnreachable_SkipsIt перестанет ловить эту поломку. При смене sortTarget_PlannedChestUnreachable_SkipsIt проверь это утверждение.");
    }

    @Test
    void events_LowMinimumAndStorage_AddsAll() {
        Set<String> events =
                StoreRules.events(Set.of("night"), new StoreRules.Signals(true, true, true, true, true, true));

        assertTrue(
                events.contains("night"),
                "Старые события не теряются"
                        + " Иначе тест events_LowMinimumAndStorage_AddsAll перестанет ловить эту поломку. При смене events_LowMinimumAndStorage_AddsAll проверь это утверждение.");
        assertTrue(
                events.contains("store_minimum"),
                "Недобор минимума — событие входа"
                        + " Иначе тест events_LowMinimumAndStorage_AddsAll перестанет ловить эту поломку. При смене events_LowMinimumAndStorage_AddsAll проверь это утверждение.");
        assertTrue(
                events.contains("storage_mixed"),
                "Смешанный сундук — событие сортировки"
                        + " Иначе тест events_LowMinimumAndStorage_AddsAll перестанет ловить эту поломку. При смене events_LowMinimumAndStorage_AddsAll проверь это утверждение.");
        assertTrue(
                events.contains("transfer_pending"),
                "Несомое — событие переноса"
                        + " Иначе тест events_LowMinimumAndStorage_AddsAll перестанет ловить эту поломку. При смене events_LowMinimumAndStorage_AddsAll проверь это утверждение.");
        assertTrue(
                events.contains("furnace_ready"),
                "Готовый выход печи — событие забрать"
                        + " Иначе тест events_LowMinimumAndStorage_AddsAll перестанет ловить эту поломку. При смене events_LowMinimumAndStorage_AddsAll проверь это утверждение.");
        assertTrue(
                events.contains("furnace_needs_fuel"),
                "Печь без топлива — событие загрузить"
                        + " Иначе тест events_LowMinimumAndStorage_AddsAll перестанет ловить эту поломку. При смене events_LowMinimumAndStorage_AddsAll проверь это утверждение.");
    }
}
