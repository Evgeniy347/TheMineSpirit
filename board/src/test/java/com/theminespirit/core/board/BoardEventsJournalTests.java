package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    void prune_CompletedActivity_DropsTicketButKeepsDoneEvent() {
        List<BoardLog.Event> events = new ArrayList<>();
        Board board = new Board(() -> 1000, (event, ticket, holder, tick) -> events.add(event));
        BoardTicket ticket = new BoardTicket(
                "activity_unload_test",
                BoardCase.ACTIVITY_UNLOAD,
                Optional.empty(),
                50,
                0,
                1,
                "activity:test",
                List.of(),
                Map.of());

        board.submit(ticket);
        board.claim("activity_unload_test", SPIRIT, 1000);
        board.complete("activity_unload_test");
        int removed = board.prune(2000, Set.of());

        assertEquals(1, removed, "Завершённая работа-занятие обязана уходить с доски, иначе она растёт за сутки");
        assertEquals(0, board.all().size(), "После prune завершённых занятий на доске не остаётся");
        assertTrue(
                events.contains(BoardLog.Event.DONE),
                "История завершённого задания обязана уйти в журнал и task_events до prune: сама доска её уже не хранит");
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
