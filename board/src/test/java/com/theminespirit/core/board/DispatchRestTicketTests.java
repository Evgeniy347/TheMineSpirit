package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DispatchRestTicketTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void choose_AnyTicket_RestHolderNotPicked() {
        Board board = new Board(() -> 100);
        board.submit(new BoardTicket(
                RestTickets.idFor("t-1"),
                BoardCase.ACTIVITY_EXTERNAL,
                Optional.of(SPIRIT),
                95,
                0,
                1,
                "always",
                List.of(),
                Map.of()));
        board.claim(RestTickets.idFor("t-1"), SPIRIT, 100);
        BoardTicket eat = new BoardTicket(
                "graph:event:hungry:eat@" + SPIRIT,
                BoardCase.EAT_TO_FULL,
                Optional.of(SPIRIT),
                100,
                0,
                1,
                "graph:event:hungry:eat",
                List.of(),
                Map.of());
        DispatchRound round = new DispatchRound(board, 100);
        DispatchRound.Candidate candidate = new DispatchRound.Candidate(
                SPIRIT, "дух", "goto", List.of(), new EatNeed(5, 20, 20, 20), SpiritView.free());

        assertTrue(
                round.choose(eat, List.of(candidate)).isEmpty(),
                "Дух с REST-тикетом не получает задания доски ни по какому пути: иначе длинная задача "
                        + "стратега снова обрывается посередине (инцидент → выдача → вытеснение)"
                        + " Иначе тест choose_AnyTicket_RestHolderNotPicked перестанет ловить эту поломку. "
                        + "При смене choose_AnyTicket_RestHolderNotPicked проверь это утверждение.");
        assertTrue(
                round.refusal(eat, candidate).isPresent(),
                "Причина отказа видна владельцу: дух держит внешнюю задачу стратега"
                        + " Иначе тест choose_AnyTicket_RestHolderNotPicked перестанет ловить эту поломку. "
                        + "При смене choose_AnyTicket_RestHolderNotPicked проверь это утверждение.");
    }

    @Test
    void claim_StuckSpirit_RestTicketClaimedAnyway() {
        Board board = new Board(() -> 100);
        board.submit(new BoardTicket(
                RestTickets.idFor("t-2"),
                BoardCase.ACTIVITY_EXTERNAL,
                Optional.of(SPIRIT),
                95,
                0,
                1,
                "always",
                List.of(),
                Map.of()));
        board.submit(new BoardTicket(
                "graph:event:hungry:eat@" + SPIRIT,
                BoardCase.EAT_TO_FULL,
                Optional.of(SPIRIT),
                100,
                0,
                1,
                "graph:event:hungry:eat",
                List.of(),
                Map.of()));
        board.stuck().mark(SPIRIT, 0, "пути нет: дух замурован");

        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(RestTickets.idFor("t-2"), SPIRIT, 100),
                "REST-тикет — прямой приказ стратега: доска обязана закрепить его даже за застрявшим "
                        + "духом, иначе тикет повиснет и retain снимет его, а доска выдаст духу свою "
                        + "работу и прервёт задачу ИИ"
                        + " Иначе тест claim_StuckSpirit_RestTicketClaimedAnyway перестанет ловить эту поломку. "
                        + "При смене claim_StuckSpirit_RestTicketClaimedAnyway проверь это утверждение.");
        assertEquals(
                BoardOutcome.SPIRIT_PAUSED,
                board.claim("graph:event:hungry:eat@" + SPIRIT, SPIRIT, 100),
                "Обычный тикет застрявшему духу по-прежнему не выдаётся: пауза после застревания "
                        + "защищает духа от немедленного повторного провала"
                        + " Иначе тест claim_StuckSpirit_RestTicketClaimedAnyway перестанет ловить эту поломку. "
                        + "При смене claim_StuckSpirit_RestTicketClaimedAnyway проверь это утверждение.");
    }
}
