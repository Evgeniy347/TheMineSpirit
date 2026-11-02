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
    private static final GraphFacts.StageReqs EMPTY = new GraphFacts.StageReqs(Map.of("hut", 1), Map.of("hut", 0));

    @Test
    void minimums_DefaultSettings_HoldOwnersStageOneNumbers() {
        StoreMinimums settings = StoreMinimums.loadDefault();

        assertEquals(64, settings.items().get("#logs").minimum(), "Владелец: брёвен минимум 64");
        assertEquals(128, settings.items().get("cobblestone").minimum(), "Владелец: булыжника минимум 128");
        assertEquals(16, settings.items().get("bread").minimum(), "Владелец: хлеба минимум 16");
        for (String tool : List.of("iron_axe", "iron_pickaxe", "iron_shovel", "iron_sword")) {
            assertEquals(1, settings.items().get(tool).minimum(), "Владелец: " + tool + " минимум 1");
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
                27));

        GraphFacts.StageReqs reqs = StoreInputs.stageReqs(EMPTY, settings, SupplyGraph.loadDefault(), chests);

        assertEquals(80, reqs.minimum().get("#logs"), "Минимум брёвен = 64 настройки + 16 резерва");
        assertEquals(160, reqs.minimum().get("cobblestone"), "Минимум булыжника = 128 + 32");
        assertEquals(15, reqs.actual().get("#logs"), "Факт по группе суммирует все брёвна в сундуках");
        assertEquals(40, reqs.actual().get("cobblestone"), "Факт булыжника берётся из сундуков");
        assertEquals(1, reqs.minimum().get("hut"), "Требования этапа не теряются");
        assertEquals(0, reqs.actual().get("hut"), "Факт этапа не теряется");
    }

    @Test
    void stageReqs_MissingItemInChests_IsZeroNotAbsent() {
        StoreMinimums settings = new StoreMinimums(Map.of("bread", new StoreMinimums.Entry(16, 4)));

        GraphFacts.StageReqs reqs = StoreInputs.stageReqs(EMPTY, settings, SupplyGraph.loadDefault(), List.of());

        assertEquals(20, reqs.minimum().get("bread"), "Минимум хлеба = 16 + 4");
        assertEquals(0, reqs.actual().get("bread"), "Нет хлеба в сундуках — факт 0, а не пропуск");
    }

    @Test
    void furnaceStation_SlotsGiven_FillsFuelInputOutputAndBusy() {
        GraphFacts.Station station = new GraphFacts.Station(
                "furnace", "furnace", new GraphFacts.Position(1, 64, 1), false, null, null, null);

        GraphFacts.Station filled = StoreInputs.furnaceStation(
                station,
                new StoreInputs.FurnaceSlots("minecraft:raw_iron", "minecraft:coal", "minecraft:iron_ingot"),
                true);

        assertTrue(filled.busy(), "Печь топится — станция занята");
        assertEquals("raw_iron", filled.input(), "Вход печи называется без пространства имён");
        assertEquals("coal", filled.fuel(), "Топливо печи называется без пространства имён");
        assertEquals("iron_ingot", filled.output(), "Выход печи называется без пространства имён");
    }

    @Test
    void furnaceStation_EmptySlots_StayNull() {
        GraphFacts.Station station = new GraphFacts.Station(
                "furnace", "furnace", new GraphFacts.Position(1, 64, 1), true, "coal", "raw_iron", "iron_ingot");

        GraphFacts.Station filled =
                StoreInputs.furnaceStation(station, new StoreInputs.FurnaceSlots(null, null, null), false);

        assertFalse(filled.busy(), "Печь не топится — станция свободна");
        assertNull(filled.input(), "Пустой вход — null");
        assertNull(filled.fuel(), "Пустое топливо — null");
        assertNull(filled.output(), "Пустой выход — null");
    }

    @Test
    void minimums_NegativeValue_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StoreMinimums.Entry(-1, 0),
                "Отрицательный минимум склада не имеет смысла");
    }
}
