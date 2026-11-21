package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoreInputsTests {
    private static final GraphSiteFacts.StageReqs EMPTY =
            new GraphSiteFacts.StageReqs(Map.of("hut", 1), Map.of("hut", 0));

    @Test
    void minimums_DefaultSettings_HoldOwnersStageOneNumbers() {
        StoreMinimums settings = StoreMinimums.loadDefault();

        assertEquals(
                64,
                settings.items().get("#logs").minimum(),
                "Владелец: брёвен минимум 64"
                        + " Иначе тест minimums_DefaultSettings_HoldOwnersStageOneNumbers перестанет ловить эту поломку. При смене minimums_DefaultSettings_HoldOwnersStageOneNumbers проверь это утверждение.");
        assertEquals(
                128,
                settings.items().get("cobblestone").minimum(),
                "Владелец: булыжника минимум 128"
                        + " Иначе тест minimums_DefaultSettings_HoldOwnersStageOneNumbers перестанет ловить эту поломку. При смене minimums_DefaultSettings_HoldOwnersStageOneNumbers проверь это утверждение.");
        assertEquals(
                16,
                settings.items().get("bread").minimum(),
                "Владелец: хлеба минимум 16"
                        + " Иначе тест minimums_DefaultSettings_HoldOwnersStageOneNumbers перестанет ловить эту поломку. При смене minimums_DefaultSettings_HoldOwnersStageOneNumbers проверь это утверждение.");
        for (String tool : List.of("iron_axe", "iron_pickaxe", "iron_shovel", "iron_sword")) {
            assertEquals(
                    1,
                    settings.items().get(tool).minimum(),
                    "Владелец: " + tool + " минимум 1"
                            + " Иначе тест minimums_DefaultSettings_HoldOwnersStageOneNumbers перестанет ловить эту поломку. При смене minimums_DefaultSettings_HoldOwnersStageOneNumbers проверь это утверждение.");
        }
    }

    @Test
    void stageReqs_ChestsPresent_AddsReserveAndRealCounts() {
        StoreMinimums settings = new StoreMinimums(Map.of(
                "#logs", new StoreMinimums.Entry(64, 16),
                "cobblestone", new StoreMinimums.Entry(128, 32)));
        List<GraphFacts.Chest> chests = List.of(new GraphFacts.Chest(
                new GraphFacts.Position(1, 64, 1),
                Map.of("minecraft:oak_log", 10, "minecraft:birch_log", 5, "minecraft:cobblestone", 40),
                2,
                27,
                GraphFacts.Reach.UNKNOWN));

        GraphSiteFacts.StageReqs reqs = StoreInputs.stageReqs(EMPTY, settings, SupplyGraph.loadDefault(), chests);

        assertEquals(
                80,
                reqs.minimum().get("#logs"),
                "Минимум брёвен = 64 настройки + 16 резерва"
                        + " Иначе тест stageReqs_ChestsPresent_AddsReserveAndRealCounts перестанет ловить эту поломку. При смене stageReqs_ChestsPresent_AddsReserveAndRealCounts проверь это утверждение.");
        assertEquals(
                160,
                reqs.minimum().get("cobblestone"),
                "Минимум булыжника = 128 + 32"
                        + " Иначе тест stageReqs_ChestsPresent_AddsReserveAndRealCounts перестанет ловить эту поломку. При смене stageReqs_ChestsPresent_AddsReserveAndRealCounts проверь это утверждение.");
        assertEquals(
                15,
                reqs.actual().get("#logs"),
                "Факт по группе суммирует все брёвна в сундуках"
                        + " Иначе тест stageReqs_ChestsPresent_AddsReserveAndRealCounts перестанет ловить эту поломку. При смене stageReqs_ChestsPresent_AddsReserveAndRealCounts проверь это утверждение.");
        assertEquals(
                40,
                reqs.actual().get("cobblestone"),
                "Факт булыжника берётся из сундуков"
                        + " Иначе тест stageReqs_ChestsPresent_AddsReserveAndRealCounts перестанет ловить эту поломку. При смене stageReqs_ChestsPresent_AddsReserveAndRealCounts проверь это утверждение.");
        assertEquals(
                1,
                reqs.minimum().get("hut"),
                "Требования этапа не теряются"
                        + " Иначе тест stageReqs_ChestsPresent_AddsReserveAndRealCounts перестанет ловить эту поломку. При смене stageReqs_ChestsPresent_AddsReserveAndRealCounts проверь это утверждение.");
        assertEquals(
                0,
                reqs.actual().get("hut"),
                "Факт этапа не теряется"
                        + " Иначе тест stageReqs_ChestsPresent_AddsReserveAndRealCounts перестанет ловить эту поломку. При смене stageReqs_ChestsPresent_AddsReserveAndRealCounts проверь это утверждение.");
    }

    @Test
    void stageReqs_MissingItemInChests_IsZeroNotAbsent() {
        StoreMinimums settings = new StoreMinimums(Map.of("bread", new StoreMinimums.Entry(16, 4)));

        GraphSiteFacts.StageReqs reqs = StoreInputs.stageReqs(EMPTY, settings, SupplyGraph.loadDefault(), List.of());

        assertEquals(
                20,
                reqs.minimum().get("bread"),
                "Минимум хлеба = 16 + 4"
                        + " Иначе тест stageReqs_MissingItemInChests_IsZeroNotAbsent перестанет ловить эту поломку. При смене stageReqs_MissingItemInChests_IsZeroNotAbsent проверь это утверждение.");
        assertEquals(
                0,
                reqs.actual().get("bread"),
                "Нет хлеба в сундуках — факт 0, а не пропуск"
                        + " Иначе тест stageReqs_MissingItemInChests_IsZeroNotAbsent перестанет ловить эту поломку. При смене stageReqs_MissingItemInChests_IsZeroNotAbsent проверь это утверждение.");
    }

    @Test
    void furnaceStation_SlotsGiven_FillsFuelInputOutputAndBusy() {
        GraphFacts.Station station = new GraphFacts.Station(
                "furnace", "furnace", new GraphFacts.Position(1, 64, 1), false, null, null, null);

        GraphFacts.Station filled = StoreInputs.furnaceStation(
                station,
                new StoreInputs.FurnaceSlots("minecraft:raw_iron", "minecraft:coal", "minecraft:iron_ingot"),
                true);

        assertTrue(
                filled.busy(),
                "Печь топится — станция занята"
                        + " Иначе тест furnaceStation_SlotsGiven_FillsFuelInputOutputAndBusy перестанет ловить эту поломку. При смене furnaceStation_SlotsGiven_FillsFuelInputOutputAndBusy проверь это утверждение.");
        assertEquals(
                "raw_iron",
                filled.input(),
                "Вход печи называется без пространства имён"
                        + " Иначе тест furnaceStation_SlotsGiven_FillsFuelInputOutputAndBusy перестанет ловить эту поломку. При смене furnaceStation_SlotsGiven_FillsFuelInputOutputAndBusy проверь это утверждение.");
        assertEquals(
                "coal",
                filled.fuel(),
                "Топливо печи называется без пространства имён"
                        + " Иначе тест furnaceStation_SlotsGiven_FillsFuelInputOutputAndBusy перестанет ловить эту поломку. При смене furnaceStation_SlotsGiven_FillsFuelInputOutputAndBusy проверь это утверждение.");
        assertEquals(
                "iron_ingot",
                filled.output(),
                "Выход печи называется без пространства имён"
                        + " Иначе тест furnaceStation_SlotsGiven_FillsFuelInputOutputAndBusy перестанет ловить эту поломку. При смене furnaceStation_SlotsGiven_FillsFuelInputOutputAndBusy проверь это утверждение.");
    }

    @Test
    void furnaceStation_EmptySlots_StayNull() {
        GraphFacts.Station station = new GraphFacts.Station(
                "furnace", "furnace", new GraphFacts.Position(1, 64, 1), true, "coal", "raw_iron", "iron_ingot");

        GraphFacts.Station filled =
                StoreInputs.furnaceStation(station, new StoreInputs.FurnaceSlots(null, null, null), false);

        assertFalse(
                filled.busy(),
                "Печь не топится — станция свободна"
                        + " Иначе тест furnaceStation_EmptySlots_StayNull перестанет ловить эту поломку. При смене furnaceStation_EmptySlots_StayNull проверь это утверждение.");
        assertNull(
                filled.input(),
                "Пустой вход — null"
                        + " Иначе тест furnaceStation_EmptySlots_StayNull перестанет ловить эту поломку. При смене furnaceStation_EmptySlots_StayNull проверь это утверждение.");
        assertNull(
                filled.fuel(),
                "Пустое топливо — null"
                        + " Иначе тест furnaceStation_EmptySlots_StayNull перестанет ловить эту поломку. При смене furnaceStation_EmptySlots_StayNull проверь это утверждение.");
        assertNull(
                filled.output(),
                "Пустой выход — null"
                        + " Иначе тест furnaceStation_EmptySlots_StayNull перестанет ловить эту поломку. При смене furnaceStation_EmptySlots_StayNull проверь это утверждение.");
    }

    @Test
    void minimums_NegativeValue_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StoreMinimums.Entry(-1, 0),
                "Отрицательный минимум склада не имеет смысла"
                        + " Иначе тест minimums_NegativeValue_Rejected перестанет ловить эту поломку. При смене minimums_NegativeValue_Rejected проверь это утверждение.");
    }
}
