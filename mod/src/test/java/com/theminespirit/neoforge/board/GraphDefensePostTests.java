package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphDefensePostTests {

    @Test
    void priority_LifeAtGraphCeiling_StaysHundred() {
        assertEquals(
                100,
                GraphDefensePost.priority(ticket(100)),
                "Оборона жизни приходит из графа на потолке 100, а BoardTicket принимает 0..100");
    }

    @Test
    void priority_BelowOne_ClampedToOne() {
        assertEquals(1, GraphDefensePost.priority(ticket(0)), "Нулевой приоритет не должен выпадать из диапазона");
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
