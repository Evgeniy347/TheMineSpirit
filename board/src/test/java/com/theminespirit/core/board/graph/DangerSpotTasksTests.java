package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import org.junit.jupiter.api.Test;

class DangerSpotTasksTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void dangerSpot_DefaultGraph_HasEscapeWithoutFenceStub() {
        GraphEvent danger = assertInstanceOf(
                GraphEvent.class,
                GRAPH.events().get("danger_spot"),
                "Событие опасного места обязано остаться: духи по-прежнему уходят домой");
        assertTrue(
                danger.tasks().stream().anyMatch(task -> task.boardCase() == BoardCase.ACTIVITY_ESCAPE),
                "Побег домой из опасного места остаётся реакцией графа: " + danger.tasks());
        assertFalse(
                danger.tasks().stream().anyMatch(task -> task.boardCase() == BoardCase.BUILD_FENCE),
                "Заглушки-заборы по опасности убраны: духи не обносят места внутри площадок плана: " + danger.tasks());
    }
}
