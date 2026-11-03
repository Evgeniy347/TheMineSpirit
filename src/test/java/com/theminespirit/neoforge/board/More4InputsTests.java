package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.More4Rules;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class More4InputsTests {
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

        GraphState next = More4Inputs.events(state, Set.of(More4Rules.PLAYER_NEAR, More4Rules.CAUTION));

        assertTrue(next.events().contains("night"), "Вход области не теряет события других сборщиков");
        assertTrue(next.events().contains(More4Rules.PLAYER_NEAR), "Чужой игрок добавляется к событиям");
        assertTrue(next.events().contains(More4Rules.CAUTION), "Осторожный режим добавляется к событиям");
        assertEquals(state.facts(), next.facts(), "Сборщик не подменяет факты графа");
    }
}
