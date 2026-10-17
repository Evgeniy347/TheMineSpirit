package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardEventsTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private record Seen(BoardLog.Event event, String ticketId, UUID holder) {
    }

    private final List<Seen> seen = new ArrayList<>();

    private Board board() {
        return new Board(() -> 7, (event, ticket, holder, tick) ->
                seen.add(new Seen(event, ticket.id(), holder)));
    }

    private static BoardTicket ticket(String id, BoardCase boardCase, String cause, List<String> dependsOn) {
        return new BoardTicket(id, boardCase, Optional.empty(), 50, 0, 1, cause, dependsOn, Map.of());
    }

    @Test
    void claim_NewAssignment_WritesOneAssignedLine() {
        Board board = board();
        board.submit(ticket("t", BoardCase.BUILD_PEN, "always", List.of()));
        seen.clear();

        board.claim("t", SPIRIT, 7);
        board.claim("t", SPIRIT, 7);

        assertEquals(List.of(new Seen(BoardLog.Event.ASSIGNED, "t", SPIRIT)), seen,
                "Выдача пишет ровно одну строку ASSIGNED с держателем: повторный claim того же духа "
                        + "не второй переход");
    }

    @Test
    void completeAndFail_BothTerminal_WriteOneLineEach() {
        Board board = board();
        board.submit(ticket("done", BoardCase.BUILD_PEN, "always", List.of()));
        board.submit(ticket("fail", BoardCase.BUILD_PEN, "always", List.of()));
        board.claim("done", SPIRIT, 7);
        board.claim("fail", SPIRIT, 7);
        seen.clear();

        board.complete("done");
        board.fail("fail");

        assertEquals(List.of(new Seen(BoardLog.Event.DONE, "done", SPIRIT),
                        new Seen(BoardLog.Event.FAILED, "fail", SPIRIT)), seen,
                "Завершение и провал пишут по одной строке с держателем: журнал видит, чем кончилось дело");
    }

    @Test
    void retain_DeadCause_WritesDetachedAndKeepsAlways() {
        Board board = board();
        board.submit(ticket("crater", BoardCase.FILL_CRATER, "crater:1:2:3", List.of()));
        board.submit(ticket("eat", BoardCase.EAT_TO_FULL, BoardTicket.ALWAYS_CAUSE, List.of()));
        seen.clear();

        board.retain(Set.of(), 7);

        assertEquals(List.of(new Seen(BoardLog.Event.DETACHED, "crater", null)), seen,
                "Снятие по актуальности пишет DETACHED, а постоянное задание с причиной always "
                        + "не снимается вовсе");
    }

    @Test
    void retain_Cascade_DetachesRootAndCancelsDependant() {
        Board board = board();
        board.submit(ticket("lure", BoardCase.LURE_ANIMAL, "animal:cow-1", List.of()));
        board.submit(ticket("dismantle", BoardCase.DISMANTLE_SHELTER, "animal:cow-1", List.of("lure")));
        seen.clear();

        board.retain(Set.of(), 7);

        assertEquals(List.of(new Seen(BoardLog.Event.DETACHED, "lure", null),
                        new Seen(BoardLog.Event.CANCELLED, "dismantle", null)), seen,
                "Каскад снимает зависимое задание отдельной строкой CANCELLED: журнал различает "
                        + "потерянную причину и снятого зависимого");
    }
}
