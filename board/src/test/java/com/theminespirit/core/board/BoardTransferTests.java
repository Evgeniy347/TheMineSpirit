package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardTransferTests {
    private static final UUID GIVER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID TAKER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @Test
    void transferTicket_ClaimAndComplete_JournalNamesHolderAndAddressee() {
        List<BoardLog.Event> events = new ArrayList<>();
        List<String> modifiers = new ArrayList<>();
        Board board = new Board(() -> 10, (event, ticket, holder, tick) -> {
            events.add(event);
            modifiers.addAll(ticket.modifiers());
            if (event == BoardLog.Event.ASSIGNED) {
                assertEquals(GIVER, holder, "Передачу исполняет дающий: груз везут от источника");
            }
        });
        BoardTicket ticket = new BoardTicket(
                "transfer:craft:CRAFT_TOOLS:stick",
                BoardCase.TRANSFER_ITEM,
                Optional.of(GIVER),
                45,
                10,
                1,
                "transfer:CRAFT_TOOLS",
                List.of(),
                Map.of(),
                List.of("to=" + TAKER, "item=stick", "count=2", "from=" + GIVER));

        assertEquals(BoardOutcome.ACCEPTED, board.submit(ticket), "Задание передачи обязано встать в очередь");
        assertTrue(
                board.claimable(new StockView(Map.of()), 10).contains(ticket),
                "Передача не ждёт предметов на складе: груз уже в инвентаре источника");
        assertEquals(BoardOutcome.CLAIMED, board.claim(ticket.id(), GIVER, 10), "Задание передачи берёт дающий дух");
        assertTrue(board.complete(ticket.id()), "Завершённая передача закрывает задание доски");

        assertEquals(
                List.of(BoardLog.Event.SUBMITTED, BoardLog.Event.ASSIGNED, BoardLog.Event.DONE),
                events,
                "Журнал обязан назвать выдачу (кому) и итог: без них передачу не отличить от зависшей");
        assertTrue(
                modifiers.contains("to=" + TAKER) && modifiers.contains("item=stick"),
                "Журнал передачи обязан назвать получателя и предмет, а не только исполнителя: " + modifiers);
        assertTrue(modifiers.contains("count=2"), "Журнал передачи обязан назвать число предметов: " + modifiers);
    }
}
