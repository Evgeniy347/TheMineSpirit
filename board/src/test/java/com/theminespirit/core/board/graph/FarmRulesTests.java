package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FarmRulesTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void herdCounts_KnownSpecies_TranslateToHerds() {
        Map<String, Integer> counts = FarmRules.herdCounts(Map.of("cow", 3, "sheep", 2, "chicken", 5));

        assertEquals(
                Map.of("herd_cow", 3, "herd_sheep", 2, "herd_chicken", 5),
                counts,
                "Вид животного переводится в узел стада");
    }

    @Test
    void events_ReadyCropsAndDryField_AreAdded() {
        Set<String> events = FarmRules.events(
                Map.of("herd_cow", 2),
                Map.of("herd_cow", 2),
                GRAPH,
                1,
                new FarmRules.Crops(4, 2, 1),
                8,
                new FarmRules.Signals(false, false, true));

        assertTrue(events.contains("crop_ready"), "Спелые грядки — событие сбора");
        assertTrue(events.contains("crop_dry"), "Сухая земля — событие полива");
        assertTrue(events.contains("field_empty"), "Свободная клетка и семена — событие посева");
    }

    @Test
    void events_HerdAtCap_AddsPenFull() {
        Set<String> events = FarmRules.events(
                Map.of("herd_cow", 10),
                Map.of("herd_cow", 10),
                GRAPH,
                1,
                new FarmRules.Crops(0, 0, 0),
                0,
                new FarmRules.Signals(false, false, true));

        assertTrue(events.contains("pen_full"), "Стадо на пределе HerdCaps — событие забоя/расширения");
    }

    @Test
    void events_AnimalsWithoutFeed_AddsStarving() {
        Set<String> events = FarmRules.events(
                Map.of("herd_cow", 3),
                Map.of("herd_cow", 3),
                GRAPH,
                1,
                new FarmRules.Crops(0, 0, 0),
                0,
                new FarmRules.Signals(false, false, false));

        assertTrue(events.contains("animal_starving"), "Животные без еды — событие кормления");
    }

    @Test
    void events_EmptyOrchardWithSapling_AddsReplant() {
        Set<String> empty = FarmRules.events(
                Map.of(), Map.of(), GRAPH, 1, new FarmRules.Crops(0, 0, 0), 0, new FarmRules.Signals(true, true, true));
        Set<String> withTree = FarmRules.events(
                Map.of(),
                Map.of(),
                GRAPH,
                1,
                new FarmRules.Crops(0, 0, 0),
                0,
                new FarmRules.Signals(false, true, true));

        assertTrue(empty.contains("orchard_empty"), "Пустая клетка сада и саженец — событие посадки");
        assertFalse(withTree.contains("orchard_empty"), "Занятая клетка сада не требует посадки");
    }
}
