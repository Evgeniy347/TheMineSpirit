package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardEventsJournalTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void transitions_SubmitClaimComplete_EmitEveryEvent() {
        List<BoardLog.Event> events = new ArrayList<>();
        Board board = new Board(() -> 42, (event, ticket, holder, tick) -> events.add(event));
        BoardTicket ticket = new BoardTicket(
                "drop_1",
                BoardCase.COLLECT_DROPS,
                Optional.empty(),
                55,
                0,
                1,
                "drop:1:2:3:minecraft:cobblestone",
                List.of(),
                Map.of());

        board.submit(ticket);
        board.claim("drop_1", SPIRIT, 42);
        board.complete("drop_1");

        assertEquals(
                List.of(BoardLog.Event.SUBMITTED, BoardLog.Event.ASSIGNED, BoardLog.Event.DONE),
                events,
                "Журнал доски обязан помнить постановку, назначение и итог: по нему видно, кто "
                        + "выкинул вещь, кто её забрал и закончил ли");
    }

    @Test
    void transitions_FailedTicket_EmitsFailed() {
        List<BoardLog.Event> events = new ArrayList<>();
        Board board = new Board(() -> 42, (event, ticket, holder, tick) -> events.add(event));
        BoardTicket ticket = new BoardTicket(
                "drop_2",
                BoardCase.COLLECT_DROPS,
                Optional.empty(),
                55,
                0,
                1,
                "drop:1:2:3:minecraft:cobblestone",
                List.of(),
                Map.of());

        board.submit(ticket);
        board.claim("drop_2", SPIRIT, 42);
        board.fail("drop_2");

        assertEquals(
                List.of(BoardLog.Event.SUBMITTED, BoardLog.Event.ASSIGNED, BoardLog.Event.FAILED),
                events,
                "Провал задания обязан попадать в журнал: без него выкинутая вещь молча остаётся в мире");
    }
}
