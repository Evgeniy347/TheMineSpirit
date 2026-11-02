package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StationFactsTests {
    private static final GraphFacts.Position AT = new GraphFacts.Position(1, 64, 1);

    @Test
    void withContents_LoadedFurnace_ReportsFuelInputOutput() {
        GraphFacts.Station station = StationFacts.withContents("furnace", AT, 2, 3, 1);

        assertTrue(station.busy(), "Печь с входом и топливом занята плавкой");
        assertEquals("fuel", station.fuel(), "Топливо в печи видно на входе графа");
        assertEquals("input", station.input(), "Вход печи виден на входе графа");
        assertEquals("output", station.output(), "Готовый выход печи виден на входе графа");
    }

    @Test
    void withContents_EmptyFurnace_IsFree() {
        GraphFacts.Station station = StationFacts.withContents("furnace", AT, 0, 0, 0);

        assertFalse(station.busy(), "Пустая печь свободна");
        assertNull(station.fuel(), "Пустая печь не имеет топлива");
        assertNull(station.input(), "Пустая печь не имеет входа");
        assertNull(station.output(), "Пустая печь не имеет выхода");
    }

    @Test
    void withContents_NegativeCounts_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> StationFacts.withContents("furnace", AT, -1, 0, 0),
                "Отрицательное количество в печи обязано ронять вход графа");

        assertTrue(error.getMessage().contains("furnace"), "Ошибка обязана назвать станцию: " + error.getMessage());
    }
}
