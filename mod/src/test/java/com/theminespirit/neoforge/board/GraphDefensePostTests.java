package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphDefensePostTests {

    @Test
    void priority_LifeAtGraphCeiling_StaysHundred() {
        int ceiling = BoardTuning.MAX_PRIORITY;
        assertEquals(
                ceiling,
                GraphDefensePost.priority(ticket(ceiling)),
                "Оборона жизни приходит из графа на потолке 100, а BoardTicket принимает 0..100");
    }

    @Test
    void priority_BelowOne_ClampedToOne() {
        assertEquals(
                GraphDefensePost.priority(ticket(1)),
                GraphDefensePost.priority(ticket(0)),
                "Нулевой приоритет не выпадает из диапазона доски: держится на нижней границе, как единица");
    }

    private static GraphTickets.Ticket ticket(int priority) {
        GraphPlan.Task task = new GraphPlan.Task(
                "event:life_threat:defend",
                BoardCase.ACTIVITY_DEFEND,
                "defend",
                Optional.empty(),
                priority,
                Map.of(),
                true,
                false,
                true,
                1);
        return new GraphTickets.Ticket("graph:event:life_threat:defend", GraphTickets.Kind.EVENT, "defend", task, 1);
    }
}
