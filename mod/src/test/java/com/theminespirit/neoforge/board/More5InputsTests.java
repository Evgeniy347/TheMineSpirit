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

        GraphState next = More5Inputs.events(state, Set.of("more5_route_blocked", "more5_wood_drops"));

        assertTrue(
                next.events().contains("night"),
                "Вход области не теряет события других сборщиков"
                        + " Иначе тест events_AddToBase_WithoutLosingThem перестанет ловить эту поломку. При смене events_AddToBase_WithoutLosingThem проверь это утверждение.");
        assertTrue(
                next.events().contains("more5_route_blocked"),
                "Событие области добавляется к событиям: без этого сборщик теряет сигнал и граф не ставит работу; "
                        + "при смене More5Inputs.events проверь наличие more5_route_blocked");
        assertTrue(
                next.events().contains("more5_wood_drops"),
                "Дропы рубки добавляются к событиям"
                        + " Иначе тест events_AddToBase_WithoutLosingThem перестанет ловить эту поломку. При смене events_AddToBase_WithoutLosingThem проверь это утверждение.");
        assertEquals(
                state.facts(),
                next.facts(),
                "Сборщик не подменяет факты графа"
                        + " Иначе тест events_AddToBase_WithoutLosingThem перестанет ловить эту поломку. При смене events_AddToBase_WithoutLosingThem проверь это утверждение.");
    }
}
