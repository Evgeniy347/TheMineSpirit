package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.graph.GraphEvent;
import com.theminespirit.core.board.graph.SupplyGraph;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LifeEventPostTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void lifeAction_InjuryHeal_HasExecutor() {
        assertEquals(
                Optional.of("heal"),
                GraphEventPost.lifeAction("heal", BoardCase.ACTIVITY_REST),
                "life_injury:heal не остаётся без исполнителя: отдых идёт тем же действием, что и more4_rest_injured");
    }

    @Test
    void lifeAction_EveryInjuryTask_IsHandled() {
        GraphEvent event = GRAPH.events().get("life_injury");
        for (GraphEvent.Task task : event.tasks()) {
            if (!task.implemented()) {
                continue;
            }
            Optional<String> action = GraphEventPost.lifeAction(task.id(), task.boardCase());
            assertTrue(
                    action.isPresent(),
                    "Задача life_injury:" + task.id() + " объявлена выполнимой, но у неё нет исполнителя: "
                            + "доска поставит билет, а дух ничего не сделает");
        }
    }
}
