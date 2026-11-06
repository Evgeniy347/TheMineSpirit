package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphState;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class More5InputsTests {
    @Test
    void events_AddToBase_WithoutLosingThem() {
        GraphState state = new GraphState(
                Map.of(),
                1,
                Map.of(),
                Map.of(),
                Map.of(),
                100,
                Set.of("night"),
                Set.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                GraphFacts.empty());

        GraphState next = More5Inputs.events(state, Set.of("more5_mine_dark", "more5_wood_drops"));

        assertTrue(next.events().contains("night"), "Вход области не теряет события других сборщиков");
        assertTrue(next.events().contains("more5_mine_dark"), "Тёмная шахта добавляется к событиям");
        assertTrue(next.events().contains("more5_wood_drops"), "Дропы рубки добавляются к событиям");
        assertEquals(state.facts(), next.facts(), "Сборщик не подменяет факты графа");
    }

    @Test
    void darkMineCells_BelowMinimum_AreNotReported() {
        assertEquals(0, More5Inputs.darkMineCells(0), "Светлая шахта работ не требует");
        assertEquals(0, More5Inputs.darkMineCells(3), "Пара тёмных клеток — ещё не повод строить освещение");
        assertEquals(4, More5Inputs.darkMineCells(4), "Порог тёмных клеток шахты даёт событие");
    }
}
