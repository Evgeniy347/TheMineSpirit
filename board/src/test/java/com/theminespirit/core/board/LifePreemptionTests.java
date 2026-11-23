package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LifePreemptionTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void displace_VitalEat_RestTicketStaysAssigned() {
        Board board = new Board(() -> 100);
        BoardTicket rest = ticket(RestTickets.idFor("t-1"), BoardCase.ACTIVITY_EXTERNAL, 95, "always");
        board.submit(rest);
        board.claim(rest.id(), SPIRIT, 100);
        BoardTicket eat =
                ticket("graph:event:hungry:eat@" + SPIRIT, BoardCase.EAT_TO_FULL, 100, "graph:event:hungry:eat");

        assertTrue(
                new LifePreemption().displace(eat, candidate(), board, true).isEmpty(),
                "Голодная задача не вытесняет REST-тикет: стратегическая задача иначе обрывается на полуслове "
                        + "и клиент видит «вытеснена вне REST»"
                        + " Иначе тест displace_VitalEat_RestTicketStaysAssigned перестанет ловить эту поломку. "
                        + "При смене displace_VitalEat_RestTicketStaysAssigned проверь это утверждение.");
        assertEquals(
                Optional.of(BoardStatus.ASSIGNED),
                board.statusOf(rest.id()),
                "REST-тикет остаётся выданным духу: доска не снимает его при голодной задаче"
                        + " Иначе тест displace_VitalEat_RestTicketStaysAssigned перестанет ловить эту поломку. "
                        + "При смене displace_VitalEat_RestTicketStaysAssigned проверь это утверждение.");
    }

    @Test
    void displace_VitalEat_GraphTicketReleased() {
        Board board = new Board(() -> 100);
        BoardTicket graph = ticket("graph:gather@x", BoardCase.GATHER_MATERIALS, 50, "graph:gather");
        board.submit(graph);
        board.claim(graph.id(), SPIRIT, 100);
        BoardTicket eat =
                ticket("graph:event:hungry:eat@" + SPIRIT, BoardCase.EAT_TO_FULL, 100, "graph:event:hungry:eat");

        assertEquals(
                Optional.of(graph),
                new LifePreemption().displace(eat, candidate(), board, true),
                "Голодная задача по-прежнему вытесняет обычное задание графа: отдых от работы не страдает"
                        + " Иначе тест displace_VitalEat_GraphTicketReleased перестанет ловить эту поломку. "
                        + "При смене displace_VitalEat_GraphTicketReleased проверь это утверждение.");
        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf(graph.id()),
                "Вытесненное задание графа возвращается в очередь: его выдадут после еды"
                        + " Иначе тест displace_VitalEat_GraphTicketReleased перестанет ловить эту поломку. "
                        + "При смене displace_VitalEat_GraphTicketReleased проверь это утверждение.");
    }

    private static BoardTicket ticket(String id, BoardCase boardCase, int priority, String cause) {
        return new BoardTicket(id, boardCase, Optional.of(SPIRIT), priority, 0, 1, cause, List.of(), Map.of());
    }

    private static DispatchRound.Candidate candidate() {
        return new DispatchRound.Candidate(
                SPIRIT, "дух", "goto", List.of(), new EatNeed(5, 20, 20, 20), SpiritView.free());
    }
}
