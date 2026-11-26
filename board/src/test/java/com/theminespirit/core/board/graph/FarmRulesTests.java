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
                "Вид животного переводится в узел стада"
                        + " Иначе тест herdCounts_KnownSpecies_TranslateToHerds перестанет ловить эту поломку. При смене herdCounts_KnownSpecies_TranslateToHerds проверь это утверждение.");
    }

    @Test
    void events_ReadyCropsAndDryField_AreAdded() {
        Set<String> events = FarmRules.events(
                Map.of("herd_cow", 2),
                Map.of("herd_cow", 2),
                GRAPH,
                1,
                new FarmRules.Crops(4, 2, 1, 3),
                new FarmRules.Signals(false, true));

        assertTrue(
                events.contains("crop_ready"),
                "Спелые грядки — событие сбора"
                        + " Иначе тест events_ReadyCropsAndDryField_AreAdded перестанет ловить эту поломку. При смене events_ReadyCropsAndDryField_AreAdded проверь это утверждение.");
        assertTrue(
                events.contains("crop_dry"),
                "Сухая земля — событие полива"
                        + " Иначе тест events_ReadyCropsAndDryField_AreAdded перестанет ловить эту поломку. При смене events_ReadyCropsAndDryField_AreAdded проверь это утверждение.");
        assertTrue(
                events.contains("field_empty"),
                "Свободная клетка и семена — событие посева"
                        + " Иначе тест events_ReadyCropsAndDryField_AreAdded перестанет ловить эту поломку. При смене events_ReadyCropsAndDryField_AreAdded проверь это утверждение.");
    }

    @Test
    void events_HerdAtCap_AddsPenFull() {
        Set<String> events = FarmRules.events(
                Map.of("herd_cow", 10),
                Map.of("herd_cow", 10),
                GRAPH,
                1,
                new FarmRules.Crops(0, 0, 0, 0),
                new FarmRules.Signals(false, true));

        assertTrue(
                events.contains("pen_full"),
                "Стадо на пределе HerdCaps — событие забоя/расширения"
                        + " Иначе тест events_HerdAtCap_AddsPenFull перестанет ловить эту поломку. При смене events_HerdAtCap_AddsPenFull проверь это утверждение.");
    }

    @Test
    void events_AnimalsWithoutFeed_AddsStarving() {
        Set<String> events = FarmRules.events(
                Map.of("herd_cow", 3),
                Map.of("herd_cow", 3),
                GRAPH,
                1,
                new FarmRules.Crops(0, 0, 0, 0),
                new FarmRules.Signals(false, false));

        assertTrue(
                events.contains("animal_starving"),
                "Животные без еды — событие кормления"
                        + " Иначе тест events_AnimalsWithoutFeed_AddsStarving перестанет ловить эту поломку. При смене events_AnimalsWithoutFeed_AddsStarving проверь это утверждение.");
    }

    @Test
    void events_EmptyFieldWithoutSeeds_AddsPlanting() {
        Set<String> events = FarmRules.events(
                Map.of(), Map.of(), GRAPH, 1, new FarmRules.Crops(0, 0, 1, 1), new FarmRules.Signals(false, true));

        assertTrue(
                events.contains("field_empty"),
                "Пустая грядка без семян всё равно должна дать событие посева: GraphEventFacts отметит"
                        + " plant_crop невыполнимым, а push wheat_seeds заставит граф добыть семена Иначе"
                        + " поле останется пустым навсегда Иначе тест events_EmptyFieldWithoutSeeds_AddsPlanting"
                        + " перестанет ловить эту поломку. При смене events_EmptyFieldWithoutSeeds_AddsPlanting"
                        + " проверь farm.json field_empty и GraphEventFacts.feasible.");
    }

    @Test
    void events_EmptyOrchardWithoutSapling_AddsReplant() {
        Set<String> events = FarmRules.events(
                Map.of(), Map.of(), GRAPH, 1, new FarmRules.Crops(0, 0, 0, 0), new FarmRules.Signals(true, true));

        assertTrue(
                events.contains("orchard_empty"),
                "Пустое место сада без саженца должно дать событие replant: граф поднимет #saplings,"
                        + " а сама посадка будет невыполнима до добычи саженца Иначе сад не восстанавливается"
                        + " после рубки Иначе тест events_EmptyOrchardWithoutSapling_AddsReplant перестанет"
                        + " ловить эту поломку. При смене events_EmptyOrchardWithoutSapling_AddsReplant проверь"
                        + " farm.json orchard_empty и GraphHarvestPost.");
    }

    @Test
    void events_EmptyOrchardWithSapling_AddsReplant() {
        Set<String> empty = FarmRules.events(
                Map.of(), Map.of(), GRAPH, 1, new FarmRules.Crops(0, 0, 0, 0), new FarmRules.Signals(true, true));
        Set<String> withTree = FarmRules.events(
                Map.of(), Map.of(), GRAPH, 1, new FarmRules.Crops(0, 0, 0, 0), new FarmRules.Signals(false, true));

        assertTrue(
                empty.contains("orchard_empty"),
                "Пустая клетка сада и саженец — событие посадки"
                        + " Иначе тест events_EmptyOrchardWithSapling_AddsReplant перестанет ловить эту поломку. При смене events_EmptyOrchardWithSapling_AddsReplant проверь это утверждение.");
        assertFalse(
                withTree.contains("orchard_empty"),
                "Занятая клетка сада не требует посадки"
                        + " Иначе тест events_EmptyOrchardWithSapling_AddsReplant перестанет ловить эту поломку. При смене events_EmptyOrchardWithSapling_AddsReplant проверь это утверждение.");
    }
}
