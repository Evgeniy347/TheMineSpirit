package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardTuning;
import org.junit.jupiter.api.Test;

class BoardDispatchDeskTests {
    @Test
    void due_BeforeRetryInterval_DoesNotRescanQueue() {
        assertFalse(
                BoardDispatchDesk.due(10, 0),
                "До истечения интервала доска не пересматривает очередь: иначе pending-тикеты будут "
                        + "каждый тик тратить бюджет выдачи. При смене due_BeforeRetryInterval_DoesNotRescanQueue "
                        + "проверь BoardTuning.dispatchRetryTicks и BoardDispatchDesk.tick.");
    }

    @Test
    void due_AfterRetryInterval_RescansPendingQueue() {
        long interval = BoardTuning.dispatchRetryTicks();

        assertTrue(
                BoardDispatchDesk.due(interval, 0),
                "После интервала pending-очередь пересматривается даже без нового билета: иначе отмена "
                        + "чужой задачи оставляет грядку без исполнителя навсегда. При смене "
                        + "due_AfterRetryInterval_RescansPendingQueue проверь живой graph:building:wheat_field "
                        + "после отмены graph:craft_oak_planks.");
    }
}
