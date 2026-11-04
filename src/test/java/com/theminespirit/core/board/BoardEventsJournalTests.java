package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void failed_WithReason_PutsReasonIntoFailedRecord() {
        List<BoardTicket> failed = new ArrayList<>();
        Board board = new Board(() -> 42, (event, ticket, holder, tick) -> {
            if (event == BoardLog.Event.FAILED) {
                failed.add(ticket);
            }
        });
        BoardTicket ticket = new BoardTicket(
                "first:build_fence",
                BoardCase.BUILD_FENCE,
                Optional.empty(),
                50,
                0,
                1,
                "first",
                List.of(),
                Map.of("oak_planks", 4));

        board.submit(ticket);
        board.claim("first:build_fence", SPIRIT, 42);
        board.fail("first:build_fence", new StockView(Map.of()), "не хватает: oak_planks 2 из 4");

        assertEquals(1, failed.size(), "Провал обязан попасть в журнал ровно одним событием FAILED");
        assertTrue(
                failed.get(0).modifiers().stream().anyMatch(detail -> detail.contains("oak_planks")),
                "FAILED в board-log обязан назвать причину провала: сейчас modifiers="
                        + failed.get(0).modifiers()
                        + ", и причину приходится искать в latest.log (наблюдение 6, лог 4)");
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

    @Test
    void vacate_WithReason_WritesReasonIntoCancelledRecord() {
        List<BoardTicket> cancelled = new ArrayList<>();
        Board board = new Board(() -> 42, (event, ticket, holder, tick) -> {
            if (event == BoardLog.Event.CANCELLED) {
                cancelled.add(ticket);
            }
        });
        board.occupy(SPIRIT, BoardCase.ACTIVITY_UNLOAD, "unload", "1,64,1", 42);

        board.vacate(SPIRIT, "задание graph:make:dirt вытеснило занятие ACTIVITY_UNLOAD");

        assertEquals(
                1,
                cancelled.size(),
                "Снятие занятия обязано попасть в журнал: иначе владелец не знает, " + "почему карточка исчезла");
        assertTrue(
                cancelled
                        .get(0)
                        .modifiers()
                        .contains("reason=задание graph:make:dirt вытеснило занятие ACTIVITY_UNLOAD"),
                "Причина отмены пишется в запись: CANCELLED без причины — это карусель карточек без разбора");
    }
}
