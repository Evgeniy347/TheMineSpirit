package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardClaimOwnerTests {
    private static final UUID PREFERRED = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTUAL = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void claim_DifferentSpirit_ReplacesPreferredOwner() {
        Board board = new Board(() -> 0L);
        BoardTicket ticket = new BoardTicket(
                "graph:event:crop_ready:harvest_crop:1",
                BoardCase.ACTIVITY_FARM,
                Optional.of(PREFERRED),
                50,
                0L,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
        board.submit(ticket);

        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(ticket.id(), ACTUAL, 0L),
                "Фактический исполнитель может забрать задание графа у предпочтительного assignee"
                        + " Иначе тест claim_DifferentSpirit_ReplacesPreferredOwner перестанет ловить эту поломку. "
                        + "При смене claim_DifferentSpirit_ReplacesPreferredOwner проверь TicketLifecycle.claim.");
        assertEquals(
                Optional.of(ACTUAL),
                board.holderOf(ticket.id()),
                "После claim держателем становится фактический исполнитель"
                        + " Иначе тест claim_DifferentSpirit_ReplacesPreferredOwner перестанет ловить эту поломку. "
                        + "При смене claim_DifferentSpirit_ReplacesPreferredOwner проверь Board.holderOf.");
        assertTrue(
                board.assignedTo(PREFERRED).isEmpty(),
                "После claim другим духом предпочтительный assignee больше не держит задание: иначе его "
                        + "жизненная нужда снимает работу фактического исполнителя и жатва получает петлю выдач"
                        + " При смене claim_DifferentSpirit_ReplacesPreferredOwner проверь BoardAssignments.holds.");
        assertEquals(
                List.of(ticket),
                board.assignedTo(ACTUAL),
                "Фактический claim-держатель остаётся единственным владельцем задания"
                        + " Иначе тест claim_DifferentSpirit_ReplacesPreferredOwner перестанет ловить эту поломку. "
                        + "При смене claim_DifferentSpirit_ReplacesPreferredOwner проверь это утверждение.");
    }
}
