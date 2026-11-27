package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GraphTicketRetryTests {
    private static final UUID SPIRIT = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    void submit_RestockFoodAfterDone_ReopensWhilePlanStillNeedsIt() {
        Board board = new Board(() -> 0L);
        BoardTicket ticket = ticket("graph:restock_food@" + SPIRIT);
        board.submit(ticket);
        board.claim(ticket.id(), SPIRIT, 0L);
        board.complete(ticket.id());

        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(ticket),
                "Текущий план графа может переоткрыть своё DONE-задание: иначе частичный привоз еды "
                        + "закроет голод навсегда и следующий потребный шаг исчезнет"
                        + " При смене submit_RestockFoodAfterDone_ReopensWhilePlanStillNeedsIt проверь TicketLifecycle.submit.");
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf(ticket.id()).orElseThrow(),
                "Повторная потребность в еде возвращает RESTOCK_FOOD в очередь"
                        + " Иначе тест submit_RestockFoodAfterDone_ReopensWhilePlanStillNeedsIt перестанет ловить эту поломку. "
                        + "При смене submit_RestockFoodAfterDone_ReopensWhilePlanStillNeedsIt проверь это утверждение.");
    }

    @Test
    void submit_GraphTicketAfterFailed_ReopensAfterBackoff() {
        long[] now = {0L};
        Board board = new Board(() -> now[0]);
        BoardTicket ticket = ticket("graph:restock_food@" + SPIRIT);
        board.submit(ticket);
        board.claim(ticket.id(), SPIRIT, 0L);
        board.fail(ticket.id());

        assertEquals(
                BoardOutcome.DUPLICATE,
                board.submit(ticket),
                "До конца backoff повторная попытка не меняет FAILED"
                        + " Иначе тест submit_GraphTicketAfterFailed_ReopensAfterBackoff перестанет ловить эту поломку. "
                        + "При смене submit_GraphTicketAfterFailed_ReopensAfterBackoff проверь это утверждение.");
        now[0] = BoardTuning.failBackoffTicks();
        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(ticket),
                "После backoff текущий план графа получает новую попытку: иначе FAILED съедает задание "
                        + "навсегда и еда больше не пополняется"
                        + " При смене submit_GraphTicketAfterFailed_ReopensAfterBackoff проверь TicketLifecycle.submit.");
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf(ticket.id()).orElseThrow(),
                "После backoff задание снова доступно выдаче"
                        + " Иначе тест submit_GraphTicketAfterFailed_ReopensAfterBackoff перестанет ловить эту поломку. "
                        + "При смене submit_GraphTicketAfterFailed_ReopensAfterBackoff проверь это утверждение.");
    }

    @Test
    void submit_GraphTicketAfterCancelled_ReopensWhenPlanReturnsIt() {
        Board board = new Board(() -> 0L);
        BoardTicket ticket = ticket("graph:event:hungry:eat:" + SPIRIT);
        board.submit(ticket);
        board.cancel(ticket.id());

        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(ticket),
                "Задание графа возвращается после снятия, если снова входит в текущий план: иначе "
                        + "терминальный статус навсегда съедает работу"
                        + " При смене submit_GraphTicketAfterCancelled_ReopensWhenPlanReturnsIt проверь TicketLifecycle.submit.");
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf(ticket.id()).orElseThrow(),
                "Вернувшееся в план задание снова ждёт исполнения"
                        + " Иначе тест submit_GraphTicketAfterCancelled_ReopensWhenPlanReturnsIt перестанет ловить эту поломку. "
                        + "При смене submit_GraphTicketAfterCancelled_ReopensWhenPlanReturnsIt проверь это утверждение.");
    }

    private static BoardTicket ticket(String id) {
        return new BoardTicket(
                id, BoardCase.RESTOCK_FOOD, Optional.of(SPIRIT), 50, 0L, 1, "graph:restock_food", List.of(), Map.of());
    }
}
